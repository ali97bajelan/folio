"""Outbound price providers; views and models only consume saved snapshots."""
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
import logging
from urllib.parse import quote
import requests
from django.conf import settings
from django.utils.dateparse import parse_datetime
from django.utils import timezone
from .crypto_catalog import find_symbol, normalized
from .models import Asset, AssetPrice, ProviderRefresh
from .services import PortfolioValuationService

logger = logging.getLogger(__name__)

class PricingError(Exception): pass
class ProviderUnavailable(PricingError): pass
class InvalidProviderResponse(PricingError): pass
class UnsupportedSymbol(PricingError): pass

@dataclass(frozen=True)
class PriceResult:
    price: Decimal; currency: str; provider: str; provider_symbol: str; fetched_at: datetime

class NobitexProvider:
    """Nobitex public market-stats endpoint; authentication is not required."""
    code = 'NOBITEX'
    request_headers = {
        'User-Agent': 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0',
        'Accept': 'application/json',
        'Referer': 'https://nobitex.ir/',
        'Origin': 'https://nobitex.ir',
        'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
        'Pragma': 'no-cache',
    }
    def supports(self, asset): return bool(asset.provider_symbol)

    def search_instruments(self, query):
        """Return the available Nobitex quote pairs for a coin code.

        Nobitex's public API does not expose a separate symbol catalogue, but
        its market-stats response for a source currency contains every active
        quote pair for that currency.  This makes the result both searchable
        and immediately usable as ``provider_symbol``.
        """
        query = (query or '').strip()
        if not query:
            return []
        try:
            source, _ = self._market_parts(query)
        except UnsupportedSymbol:
            # Resolve Persian/English names and partial local suggestions before
            # contacting Nobitex, whose API accepts symbols only.
            source = (find_symbol(query) or query).lower().replace('_', '').replace('-', '').replace('/', '')
        if not source or not source.isalnum():
            return []
        try:
            response = requests.get(
                f'{settings.NOBITEX_BASE_URL}/market/stats',
                params={'srcCurrency': source}, headers=self.request_headers,
                timeout=(settings.NOBITEX_CONNECT_TIMEOUT, settings.NOBITEX_READ_TIMEOUT),
            )
            response.raise_for_status()
            payload = response.json()
            if payload.get('status') != 'ok':
                raise InvalidProviderResponse('Nobitex did not return an OK response.')
            stats = payload['stats']
        except requests.Timeout as exc:
            raise ProviderUnavailable('Nobitex search timed out.') from exc
        except requests.ConnectionError as exc:
            raise ProviderUnavailable('Cannot resolve or connect to Nobitex.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            raise ProviderUnavailable(f'Nobitex search returned HTTP {status}.') from exc
        except (AttributeError, KeyError, TypeError, ValueError) as exc:
            raise InvalidProviderResponse('Nobitex returned an unexpected search response.') from exc

        results = []
        for market in stats:
            try:
                market_source, target = market.lower().split('-', 1)
            except ValueError:
                continue
            if market_source != source:
                continue
            display_target = 'IRT' if target == 'rls' else target.upper()
            results.append({
                'provider_symbol': f'{market_source}{display_target}'.upper(),
                'asset_symbol': market_source.upper(),
                'symbol': f'{market_source.upper()}/{display_target}',
                'name': f'Nobitex {market_source.upper()} / {display_target}',
                'meta': 'Nobitex market',
            })
        return results[:12]

    @staticmethod
    def _market_parts(provider_symbol):
        """Return Nobitex's lowercase ``source-target`` market key.

        Existing assets use compact symbols such as BTCUSDT and USDTIRT.  Nobitex
        calls its rial quote ``rls``, while prices saved by this application use
        toman (IRT).
        """
        symbol = provider_symbol.strip().lower().replace('_', '-').replace('/', '-')
        if '-' in symbol:
            source, target = symbol.split('-', 1)
        else:
            for suffix in ('usdt', 'irt', 'rls'):
                if symbol.endswith(suffix) and len(symbol) > len(suffix):
                    source, target = symbol[:-len(suffix)], suffix
                    break
            else:
                raise UnsupportedSymbol('Use a Nobitex pair such as BTCUSDT or USDTIRT.')
        if target == 'irt':
            target = 'rls'
        if not source or not target:
            raise UnsupportedSymbol('Use a valid Nobitex source-target pair.')
        return source, target

    def fetch_price(self, asset):
        if not self.supports(asset): raise UnsupportedSymbol('Provider symbol is required.')
        source, target = self._market_parts(asset.provider_symbol)
        market = f'{source}-{target}'
        url = f'{settings.NOBITEX_BASE_URL}/market/stats'
        logger.info('Nobitex request started: asset=%s market=%s url=%s', asset.symbol, market, url)
        try:
            response = requests.get(
                url,
                params={'srcCurrency': source},
                headers=self.request_headers,
                timeout=(settings.NOBITEX_CONNECT_TIMEOUT, settings.NOBITEX_READ_TIMEOUT),
            )
            logger.info('Nobitex response received: asset=%s market=%s http_status=%s', asset.symbol, market, response.status_code)
            response.raise_for_status()
        except requests.Timeout as exc:
            logger.warning('Nobitex request timed out: asset=%s market=%s', asset.symbol, market)
            raise ProviderUnavailable('Nobitex request timed out.') from exc
        except requests.ConnectionError as exc:
            logger.warning('Nobitex connection failed: asset=%s market=%s reason=%s', asset.symbol, market, exc)
            raise ProviderUnavailable('Cannot resolve or connect to apiv2.nobitex.ir.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            logger.warning('Nobitex HTTP failure: asset=%s market=%s http_status=%s', asset.symbol, market, status)
            raise ProviderUnavailable(f'Nobitex returned HTTP {status}.') from exc
        except requests.RequestException as exc:
            logger.warning('Nobitex request failed: asset=%s market=%s reason=%s', asset.symbol, market, exc)
            raise ProviderUnavailable('Nobitex is unavailable.') from exc
        try:
            payload = response.json()
            if payload.get('status') != 'ok': raise InvalidProviderResponse('Nobitex did not return an OK response.')
            raw_price = Decimal(str(payload['stats'][market]['latest']))
            if raw_price <= 0: raise InvalidProviderResponse('Nobitex returned an invalid price.')
            # Nobitex labels rial markets RLS; the app's IRT values are toman.
            currency = 'IRT' if target == 'rls' else target.upper()
            price = raw_price / Decimal('10') if target == 'rls' else raw_price
            captured_at = timezone.now()
        except (KeyError, TypeError, ValueError, InvalidOperation) as exc:
            raise InvalidProviderResponse('Nobitex returned an unexpected market response.') from exc
        provider_symbol = asset.provider_symbol.strip().upper()
        logger.info('Nobitex price parsed: asset=%s market=%s price=%s currency=%s captured_at=%s', asset.symbol, market, price, currency, captured_at.isoformat())
        return PriceResult(price, currency, self.code, provider_symbol, captured_at)

class AbanTetherProvider:
    """Aban Tether public coin catalogue, with Persian and English names."""
    code = 'ABANTETHER'
    request_headers = {
        'User-Agent': 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0',
        'Accept': '*/*',
        'Referer': 'https://abantether.com/',
        'Origin': 'https://abantether.com',
    }

    def supports(self, asset):
        return bool(asset.provider_symbol)

    def _coins(self):
        try:
            response = requests.get(
                settings.ABANTETHER_COIN_CATALOG_URL, headers=self.request_headers,
                timeout=(settings.ABANTETHER_CONNECT_TIMEOUT, settings.ABANTETHER_READ_TIMEOUT),
            )
            response.raise_for_status()
            coins = response.json()['data']
            if not isinstance(coins, list):
                raise InvalidProviderResponse('Aban Tether returned an unexpected coin response.')
            return coins
        except requests.Timeout as exc:
            raise ProviderUnavailable('Aban Tether request timed out.') from exc
        except requests.ConnectionError as exc:
            raise ProviderUnavailable('Cannot connect to Aban Tether.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            raise ProviderUnavailable(f'Aban Tether returned HTTP {status}.') from exc
        except (AttributeError, KeyError, TypeError, ValueError) as exc:
            raise InvalidProviderResponse('Aban Tether returned an unexpected coin response.') from exc

    @staticmethod
    def _price(coin):
        try:
            buy = Decimal(str(coin['price_buy']))
            sell = Decimal(str(coin['price_sell']))
            if buy <= 0 or sell <= 0:
                raise ValueError
            return (buy + sell) / Decimal('2')
        except (KeyError, TypeError, ValueError, InvalidOperation) as exc:
            raise InvalidProviderResponse('Aban Tether returned an invalid coin price.') from exc

    def fetch_price(self, asset):
        provider_symbol = asset.provider_symbol.strip().upper()
        coin = next((item for item in self._coins() if str(item.get('symbol', '')).upper() == provider_symbol), None)
        if coin is None or not coin.get('is_active'):
            raise UnsupportedSymbol(f'No active Aban Tether asset exists for {provider_symbol}.')
        return PriceResult(self._price(coin), 'IRT', self.code, provider_symbol, timezone.now())

    def search_instruments(self, query):
        query = (query or '').strip()
        if not query:
            return []
        needle = normalized(query)
        results = []
        for coin in self._coins():
            base_symbol = str(coin.get('symbol', '')).upper()
            searchable = (base_symbol, coin.get('name', ''), coin.get('persian_name', ''))
            if not coin.get('is_active') or not any(needle in normalized(value) for value in searchable):
                continue
            results.append({
                'provider_symbol': base_symbol,
                'asset_symbol': base_symbol,
                'symbol': base_symbol,
                'name': coin.get('persian_name') or coin.get('name') or base_symbol,
                'meta': coin.get('name', 'Aban Tether asset'),
            })
        return results[:12]

class TsetmcProvider:
    """TSETMC public closing-price feed.

    ``provider_symbol`` is TSETMC's numeric InsCode, rather than the ticker.
    InsCodes remain stable when a Persian ticker or company name changes.  TSETMC
    quotes prices in rials; portfolio IRT values are toman, so the value is
    divided by ten before it is saved.
    """
    code = 'TSETMC'
    request_headers = {
        'User-Agent': 'Mozilla/5.0 (compatible; AssetsDashboard/1.0)',
        'Accept': 'application/json',
        'Referer': 'https://www.tsetmc.com/',
    }

    def supports(self, asset):
        return bool(asset.provider_symbol and asset.provider_symbol.strip().isdigit())

    def search_instruments(self, query):
        """Return a compact, safe-to-expose list of TSETMC search matches."""
        query = (query or '').strip()
        if not query:
            return []
        url = f'{settings.TSETMC_BASE_URL}/Instrument/GetInstrumentSearch/{quote(query, safe="")}'
        try:
            response = requests.get(
                url, headers=self.request_headers,
                timeout=(settings.TSETMC_CONNECT_TIMEOUT, settings.TSETMC_READ_TIMEOUT),
            )
            response.raise_for_status()
            instruments = response.json()['instrumentSearch']
        except requests.Timeout as exc:
            raise ProviderUnavailable('TSETMC search timed out.') from exc
        except requests.ConnectionError as exc:
            raise ProviderUnavailable('Cannot connect to TSETMC.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            raise ProviderUnavailable(f'TSETMC search returned HTTP {status}.') from exc
        except (KeyError, TypeError, ValueError) as exc:
            raise InvalidProviderResponse('TSETMC returned an unexpected search response.') from exc
        return [
            {
                'provider_symbol': str(item['insCode']),
                'asset_symbol': item.get('lVal18AFC', '') or str(item['insCode']),
                'symbol': item.get('lVal18AFC', ''),
                'name': item.get('lVal30', ''),
                'meta': f"InsCode: {item['insCode']}",
            }
            for item in instruments[:12]
            if item.get('insCode')
        ]

    def fetch_price(self, asset):
        ins_code = asset.provider_symbol.strip()
        if not ins_code.isdigit():
            raise UnsupportedSymbol('TSETMC requires a numeric InsCode, not a ticker symbol.')
        url = f'{settings.TSETMC_BASE_URL}/ClosingPrice/GetClosingPriceInfo/{ins_code}'
        logger.info('TSETMC request started: asset=%s ins_code=%s', asset.symbol, ins_code)
        try:
            response = requests.get(
                url, headers=self.request_headers,
                timeout=(settings.TSETMC_CONNECT_TIMEOUT, settings.TSETMC_READ_TIMEOUT),
            )
            logger.info('TSETMC response received: asset=%s ins_code=%s http_status=%s', asset.symbol, ins_code, response.status_code)
            response.raise_for_status()
        except requests.Timeout as exc:
            raise ProviderUnavailable('TSETMC request timed out.') from exc
        except requests.ConnectionError as exc:
            raise ProviderUnavailable('Cannot connect to TSETMC.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            raise ProviderUnavailable(f'TSETMC returned HTTP {status}.') from exc
        except requests.RequestException as exc:
            raise ProviderUnavailable('TSETMC is unavailable.') from exc
        try:
            quote = response.json()['closingPriceInfo']
            # pClosing is the official close in rial. Some older response
            # shapes use pDrCotVal for the same value.
            raw_price = Decimal(str(quote.get('pClosing', quote.get('pDrCotVal'))))
            if raw_price <= 0:
                raise InvalidProviderResponse('TSETMC returned an invalid closing price.')
        except (KeyError, TypeError, ValueError, InvalidOperation) as exc:
            raise InvalidProviderResponse('TSETMC returned an unexpected quote response.') from exc
        return PriceResult(raw_price / Decimal('10'), 'IRT', self.code, ins_code, timezone.now())

class RahavardProvider:
    """Rahavard 365 public instrument search.

    Rahavard identifies an instrument with its numeric ``entity_id``.  The
    search endpoint is public, but its price-quote endpoint has not yet been
    configured, so selected instruments retain manual pricing for now.
    """
    code = 'RAHAVARD'
    request_headers = {
        'User-Agent': 'Mozilla/5.0 (compatible; AssetsDashboard/1.0)',
        'Accept': 'application/json, text/plain, */*',
        'Application-Name': 'rahavard',
        'Platform': 'web',
        'Referer': 'https://rahavard365.com/',
    }

    def supports(self, asset): return bool(asset.provider_symbol and asset.provider_symbol.strip().isdigit())

    def search_instruments(self, query, asset_type=''):
        query = (query or '').strip()
        if not query:
            return []
        query_words = [normalized(word) for word in query.split() if normalized(word)]
        is_precious_metal_search = any(
            metal in word
            for word in query_words
            for metal in ('طلا', 'نقره', 'gold', 'silver')
        )
        try:
            response = requests.get(
                f'{settings.RAHAVARD_BASE_URL}/search', params={'keyword': query},
                headers=self.request_headers,
                timeout=(settings.RAHAVARD_CONNECT_TIMEOUT, settings.RAHAVARD_READ_TIMEOUT),
            )
            response.raise_for_status()
            instruments = response.json()['data']
            if not isinstance(instruments, list):
                raise InvalidProviderResponse('Rahavard returned an unexpected search response.')
        except requests.Timeout as exc:
            raise ProviderUnavailable('Rahavard search timed out.') from exc
        except requests.ConnectionError as exc:
            raise ProviderUnavailable('Cannot connect to Rahavard.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            raise ProviderUnavailable(f'Rahavard search returned HTTP {status}.') from exc
        except (AttributeError, KeyError, TypeError, ValueError) as exc:
            raise InvalidProviderResponse('Rahavard returned an unexpected search response.') from exc

        preferred_type = {
            'GOLD': 'کالا',
            'SILVER': 'کالا',
            'CRYPTO': 'رمز ارز',
            'IRAN_STOCK': 'سهام',
            'US_STOCK': 'سهام',
        }.get((asset_type or '').upper())
        results = []
        for item in instruments:
            entity_id = item.get('entity_id')
            name = str(item.get('name') or '')
            name_words = [normalized(word) for word in name.split() if normalized(word)]
            if (
                not entity_id
                or item.get('unlisted_item')
                or not name
                # Match from the beginning of a name word.  A raw substring
                # check would incorrectly treat "طلا" as matching "اطلاعات".
                or not all(any(name_word.startswith(word) for name_word in name_words) for word in query_words)
                or (is_precious_metal_search and item.get('exchange') != '')
            ):
                continue
            ticker = item.get('trade_symbol') or item.get('short_name') or item.get('name')
            if not ticker:
                continue
            results.append((item.get('type') != preferred_type, {
                'provider_symbol': str(entity_id),
                'asset_symbol': str(ticker),
                'symbol': str(ticker),
                'name': item.get('short_name') or name or str(ticker),
                'meta': ' · '.join(filter(None, (item.get('exchange'), item.get('type'), f'Rahavard ID: {entity_id}'))),
            }))
        # Python's stable sort preserves Rahavard's relevance order within each
        # type, while placing the selected asset category first.
        results.sort(key=lambda result: result[0])
        return [result for _, result in results[:12]]

    def fetch_price(self, asset):
        entity_id = asset.provider_symbol.strip()
        if not entity_id.isdigit():
            raise UnsupportedSymbol('Rahavard requires a numeric entity ID.')
        url = f'{settings.RAHAVARD_BASE_URL}/asset/{entity_id}'
        try:
            response = requests.get(
                url, headers=self.request_headers,
                timeout=(settings.RAHAVARD_CONNECT_TIMEOUT, settings.RAHAVARD_READ_TIMEOUT),
            )
            response.raise_for_status()
            data = response.json()['data']
            header_trade = data.get('header_last_trade') or {}
            last_trade = data.get('last_trade') or {}
            raw_price = header_trade.get('real_close_price') or last_trade.get('close_price')
            price = Decimal(str(raw_price))
            if price <= 0:
                raise ValueError
            captured_at = parse_datetime(header_trade.get('end_date_time') or last_trade.get('end_date_time') or '') or timezone.now()
        except requests.Timeout as exc:
            raise ProviderUnavailable('Rahavard price request timed out.') from exc
        except requests.ConnectionError as exc:
            raise ProviderUnavailable('Cannot connect to Rahavard.') from exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response else 'unknown'
            raise ProviderUnavailable(f'Rahavard price request returned HTTP {status}.') from exc
        except (AttributeError, KeyError, TypeError, ValueError, InvalidOperation) as exc:
            raise InvalidProviderResponse('Rahavard returned an unexpected price response.') from exc
        # Rahavard quotes Iranian market prices in rial; portfolio IRT values
        # are toman, matching the conversion used for TSETMC prices.
        return PriceResult(price / Decimal('10'), 'IRT', self.code, entity_id, captured_at)

PROVIDERS = {'NOBITEX': NobitexProvider(), 'ABANTETHER': AbanTetherProvider(), 'TSETMC': TsetmcProvider(), 'RAHAVARD': RahavardProvider()}

def refresh_prices():
    """Refresh each provider at most once per configured interval."""
    now = timezone.now(); outcomes = []; interval = getattr(settings, 'PRICE_REFRESH_MIN_INTERVAL', 60)
    for code, provider in PROVIDERS.items():
        last = ProviderRefresh.objects.filter(provider=code, status='SUCCESS').first()
        if last and (now-last.started_at).total_seconds() < interval:
            logger.info('Price refresh skipped: provider=%s reason=throttled interval=%ss', code, interval)
            outcomes.append((code, 'cached')); continue
        # iexact keeps projects created before provider choices were introduced
        # working (their provider values may be lowercase).
        assets = list(Asset.objects.filter(is_active=True, pricing_mode='MARKET', price_provider__iexact=code))
        refresh = ProviderRefresh.objects.create(provider=code, started_at=now, status='SUCCESS'); failures=[]
        for asset in assets:
            try:
                price = provider.fetch_price(asset)
                AssetPrice.objects.create(asset=asset, price=price.price, currency=price.currency, provider=price.provider, provider_symbol=price.provider_symbol, captured_at=price.fetched_at)
                logger.info('Price snapshot stored: provider=%s asset=%s price=%s currency=%s', code, asset.symbol, price.price, price.currency)
            except PricingError as exc:
                logger.warning('Price refresh failed: provider=%s asset=%s reason=%s', code, asset.symbol, exc)
                failures.append(f'{asset.symbol}: {exc}')
        refresh.finished_at = timezone.now()
        if failures:
            refresh.status = 'PARTIAL' if len(failures) < len(assets) else 'FAILED'; refresh.error_message = ' | '.join(failures)
        refresh.save()
        if not failures:
            outcomes.append((code, 'updated'))
        elif len(failures) == len(assets):
            outcomes.append((code, 'unavailable — cached prices retained'))
        else:
            outcomes.append((code, 'partially updated — cached prices retained'))
    PortfolioValuationService.save_snapshots(now)
    return outcomes

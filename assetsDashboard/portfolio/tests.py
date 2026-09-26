from datetime import timedelta
from decimal import Decimal
from unittest.mock import Mock, patch
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from .models import Asset, AssetPrice, AssetValuation, Location, Transaction
from .pricing import AbanTetherProvider, NobitexProvider, RahavardProvider, TsetmcProvider
from .services import PortfolioValuationService
from .templatetags.portfolio_format import decimal_trim, irt_compact, irt_whole


class IrtCompactFormatTests(TestCase):
    def test_formats_millions_and_billions_with_one_decimal(self):
        self.assertEqual(irt_compact('141268573'), '141.2M')
        self.assertEqual(irt_compact('1412685730'), '1.4B')
        self.assertEqual(irt_compact('668000000'), '668M')

    def test_keeps_smaller_values_grouped(self):
        self.assertEqual(irt_compact('452400'), '452,400')

    def test_irt_whole_truncates_fractional_toman(self):
        self.assertEqual(irt_whole('24011000.55'), '24,011,000')

    def test_decimal_trim_removes_only_insignificant_fractional_zeroes(self):
        self.assertEqual(decimal_trim('49.7850'), '49.785')
        self.assertEqual(decimal_trim('755.00'), '755')


class DashboardTrendTests(TestCase):
    def test_dashboard_shows_portfolio_change_percentages(self):
        now = timezone.now()
        asset = Asset.objects.create(name='Bitcoin', symbol='BTC-CHANGE', asset_type='CRYPTO')
        Transaction.objects.create(asset=asset, transaction_type='BUY', quantity='1', executed_at=now - timedelta(days=31))
        AssetPrice.objects.create(asset=asset, price='100', currency='IRT', provider='MANUAL', captured_at=now - timedelta(days=31))
        AssetPrice.objects.create(asset=asset, price='110', currency='IRT', provider='MANUAL', captured_at=now - timedelta(days=7))
        AssetPrice.objects.create(asset=asset, price='90', currency='IRT', provider='MANUAL', captured_at=now)

        response = self.client.get(reverse('dashboard'))

        changes = response.context['changes']
        self.assertEqual([change['label'] for change in changes], ['1 day', '7 days', '30 days'])
        self.assertEqual([change['percent'] for change in changes], [Decimal('-18.18181818181818181818181818'), Decimal('-18.18181818181818181818181818'), Decimal('-10')])
        self.assertContains(response, 'PORTFOLIO CHANGE')
        self.assertContains(response, 'change-down')

    def test_dashboard_uses_requested_trend_period(self):
        response = self.client.get(reverse('dashboard'), {'trend': '1w'})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.context['trend_period'], '1w')
        self.assertEqual(len(response.context['history']), 8)
        self.assertContains(response, 'All time')
        self.assertNotContains(response, 'timedelta')
        self.assertContains(response, "mode:'index'")
        self.assertContains(response, 'context.parsed.y')

    def test_dashboard_defaults_to_one_month_for_invalid_period(self):
        response = self.client.get(reverse('dashboard'), {'trend': 'unknown'})

        self.assertEqual(response.context['trend_period'], '1m')

    def test_dashboard_serializes_chart_values_as_json(self):
        asset = Asset.objects.create(name='Bitcoin', symbol='BTC', asset_type='CRYPTO')
        Transaction.objects.create(asset=asset, transaction_type='BUY', quantity='1', executed_at=timezone.now())
        AssetPrice.objects.create(asset=asset, price='320071031.2', currency='IRT', provider='MANUAL', captured_at=timezone.now())

        response = self.client.get(reverse('dashboard'))

        self.assertContains(response, 'id="dashboard-chart-data"')
        self.assertContains(response, '320071031.2')
        self.assertNotContains(response, '320,071,031.2')

    def test_asset_list_defaults_to_descending_toman_value(self):
        low = Asset.objects.create(name='Low value', symbol='LOW', asset_type='CRYPTO')
        high = Asset.objects.create(name='High value', symbol='HIGH', asset_type='CRYPTO')
        for asset, price in ((low, '100'), (high, '200')):
            Transaction.objects.create(asset=asset, transaction_type='BUY', quantity='1', executed_at=timezone.now())
            AssetPrice.objects.create(asset=asset, price=price, currency='IRT', provider='MANUAL', captured_at=timezone.now())

        response = self.client.get(reverse('asset_list'))

        self.assertEqual([item['asset'].symbol for item in response.context['assets']], ['HIGH', 'LOW'])
        self.assertEqual(response.context['sort_by'], 'value_desc')


class NobitexProviderTests(TestCase):
    def asset(self, symbol):
        return Asset.objects.create(name=symbol, symbol=symbol, asset_type='CRYPTO', unit='UNIT', pricing_mode='MARKET', price_provider='NOBITEX', provider_symbol=symbol)
    @patch('portfolio.pricing.requests.get')
    def test_irt_price_is_converted_from_rial_to_toman(self, get):
        response=Mock(); response.json.return_value={'status':'ok','stats':{'btc-rls':{'latest':'123450'}}}; get.return_value=response
        result=NobitexProvider().fetch_price(self.asset('BTCIRT'))
        self.assertEqual(str(result.price),'12345'); self.assertEqual(result.currency,'IRT')
        get.assert_called_once_with(
            'https://apiv2.nobitex.ir/market/stats',
            params={'srcCurrency': 'btc'}, headers=NobitexProvider.request_headers,
            timeout=(3, 5),
        )
    @patch('portfolio.pricing.requests.get')
    def test_usdt_quote_stays_usdt(self, get):
        response=Mock(); response.json.return_value={'status':'ok','stats':{'btc-usdt':{'latest':'65000.5'}}}; get.return_value=response
        result=NobitexProvider().fetch_price(self.asset('BTCUSDT'))
        self.assertEqual(str(result.price),'65000.5'); self.assertEqual(result.currency,'USDT')

    @patch('portfolio.pricing.requests.get')
    def test_search_returns_active_market_pairs(self, get):
        response = Mock(); response.json.return_value = {'status': 'ok', 'stats': {'btc-rls': {}, 'btc-usdt': {}}}; get.return_value = response

        results = NobitexProvider().search_instruments('BTC')

        self.assertEqual([result['provider_symbol'] for result in results], ['BTCIRT', 'BTCUSDT'])
        get.assert_called_once_with('https://apiv2.nobitex.ir/market/stats', params={'srcCurrency': 'btc'}, headers=NobitexProvider.request_headers, timeout=(3, 5))

    @patch('portfolio.pricing.requests.get')
    def test_search_resolves_a_persian_crypto_name(self, get):
        response = Mock(); response.json.return_value = {'status': 'ok', 'stats': {'btc-rls': {}}}; get.return_value = response

        results = NobitexProvider().search_instruments('بیت کوین')

        self.assertEqual(results[0]['provider_symbol'], 'BTCIRT')
        self.assertEqual(get.call_args.kwargs['params'], {'srcCurrency': 'btc'})

    def test_btc_usdt_value_uses_usdtirt_asset_as_fx_rate(self):
        btc=self.asset('BTC')
        fx=Asset.objects.create(name='USDT / Toman',symbol='USDTIRT',asset_type='USD',unit='UNIT',pricing_mode='MARKET',price_provider='NOBITEX',provider_symbol='USDTIRT')
        Transaction.objects.create(asset=btc,transaction_type='BUY',quantity='2',executed_at=timezone.now())
        AssetPrice.objects.create(asset=btc,price='50000',currency='USDT',provider='NOBITEX',captured_at=timezone.now())
        AssetPrice.objects.create(asset=fx,price='80000',currency='IRT',provider='NOBITEX',captured_at=timezone.now())
        self.assertEqual(PortfolioValuationService.asset_value(btc), 8000000000)

    def test_valuation_snapshot_saves_usdt_and_toman_values(self):
        btc=self.asset('BTC')
        fx=Asset.objects.create(name='USDT / Toman',symbol='USDTIRT',asset_type='USD',unit='UNIT',pricing_mode='MARKET',price_provider='NOBITEX',provider_symbol='USDTIRT')
        Transaction.objects.create(asset=btc,transaction_type='BUY',quantity='0.0087',executed_at=timezone.now())
        AssetPrice.objects.create(asset=btc,price='65000',currency='USDT',provider='NOBITEX',captured_at=timezone.now())
        AssetPrice.objects.create(asset=fx,price='80000',currency='IRT',provider='NOBITEX',captured_at=timezone.now())
        PortfolioValuationService.save_snapshots()
        value=AssetValuation.objects.get(asset=btc)
        self.assertEqual(value.usdt_value, 565.5)
        self.assertEqual(value.toman_value, 45240000)

    def test_usdt_asset_symbol_is_accepted_as_the_toman_exchange_rate(self):
        btc=self.asset('BTC')
        fx=Asset.objects.create(name='Tether',symbol='USDT',asset_type='USD',unit='UNIT',pricing_mode='MARKET',price_provider='NOBITEX',provider_symbol='USDTIRT')
        Transaction.objects.create(asset=btc,transaction_type='BUY',quantity='0.0087',executed_at=timezone.now())
        AssetPrice.objects.create(asset=btc,price='65000',currency='USDT',provider='NOBITEX',captured_at=timezone.now())
        AssetPrice.objects.create(asset=fx,price='80000',currency='IRT',provider='NOBITEX',captured_at=timezone.now())
        self.assertEqual(PortfolioValuationService.asset_value(btc, 'IRT'), 45240000)

    def test_asset_creation_accepts_opening_holdings_at_multiple_locations(self):
        nobitex=Location.objects.create(name='Nobitex')
        wallex=Location.objects.create(name='Wallex')
        response=self.client.post(reverse('asset_create'), {
            'name':'Bitcoin', 'symbol':'BTC-MULTI', 'asset_type':'CRYPTO', 'unit':'UNIT',
            'pricing_mode':'MANUAL', 'price_provider':'', 'provider_symbol':'', 'manual_price_currency':'IRT', 'is_active':'on',
            'holdings-TOTAL_FORMS':'3', 'holdings-INITIAL_FORMS':'0', 'holdings-MIN_NUM_FORMS':'0', 'holdings-MAX_NUM_FORMS':'1000',
            'holdings-0-location':nobitex.pk, 'holdings-0-quantity':'0.0087',
            'holdings-1-location':wallex.pk, 'holdings-1-quantity':'0.015',
        })
        self.assertEqual(response.status_code, 302)
        asset=Asset.objects.get(symbol='BTC-MULTI')
        self.assertEqual(asset.transactions.count(), 2)
        self.assertEqual(set(asset.transactions.values_list('location__name', flat=True)), {'Nobitex', 'Wallex'})

    def test_new_asset_form_starts_with_one_holding_row_without_delete_checkbox(self):
        response = self.client.get(reverse('asset_create'))

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'id="add-holding"')
        self.assertContains(response, 'name="holdings-0-location"')
        self.assertNotContains(response, 'holdings-1-location')
        self.assertNotContains(response, 'holdings-0-DELETE')

    def test_asset_delete_removes_its_transactions(self):
        asset=self.asset('DELETE-ME')
        Transaction.objects.create(asset=asset,transaction_type='BUY',quantity='1',executed_at=timezone.now())
        response=self.client.post(reverse('asset_delete', args=[asset.pk]))
        self.assertRedirects(response, reverse('asset_list'))
        self.assertFalse(Asset.objects.filter(pk=asset.pk).exists())
        self.assertEqual(Transaction.objects.count(), 0)


class TsetmcProviderTests(TestCase):
    def asset(self, provider_symbol='35425587644337450'):
        return Asset.objects.create(
            name='Foolad Mobarakeh', symbol='FOOLAD', asset_type='IRAN_STOCK',
            unit='UNIT', pricing_mode='MARKET', price_provider='TSETMC',
            provider_symbol=provider_symbol,
        )

    @patch('portfolio.pricing.requests.get')
    def test_closing_price_is_converted_from_rial_to_toman(self, get):
        response = Mock()
        response.status_code = 200
        response.json.return_value = {'closingPriceInfo': {'pClosing': 123450}}
        get.return_value = response

        result = TsetmcProvider().fetch_price(self.asset())

        self.assertEqual(result.price, 12345)
        self.assertEqual(result.currency, 'IRT')
        self.assertEqual(result.provider, 'TSETMC')
        get.assert_called_once_with(
            'https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/35425587644337450',
            headers=TsetmcProvider.request_headers, timeout=(3, 8),
        )

    def test_rejects_a_ticker_instead_of_ins_code(self):
        with self.assertRaisesMessage(Exception, 'numeric InsCode'):
            TsetmcProvider().fetch_price(self.asset('فولاد'))

    @patch('portfolio.pricing.requests.get')
    def test_search_returns_ticker_name_and_ins_code(self, get):
        response = Mock()
        response.json.return_value = {'instrumentSearch': [{
            'insCode': 35425587644337450, 'lVal18AFC': 'فولاد', 'lVal30': 'فولاد مبارکه اصفهان',
        }]}
        get.return_value = response

        results = TsetmcProvider().search_instruments('فولاد')

        self.assertEqual(results, [{'provider_symbol': '35425587644337450', 'asset_symbol': 'فولاد', 'symbol': 'فولاد', 'name': 'فولاد مبارکه اصفهان', 'meta': 'InsCode: 35425587644337450'}])
        get.assert_called_once_with(
            'https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/%D9%81%D9%88%D9%84%D8%A7%D8%AF',
            headers=TsetmcProvider.request_headers, timeout=(3, 8),
        )

    @patch('portfolio.views.PROVIDERS')
    def test_search_endpoint_returns_provider_results(self, search):
        provider = Mock(); provider.search_instruments.return_value = [{'provider_symbol': '1', 'symbol': 'TEST', 'name': 'Test Company'}]
        search.get.return_value = provider

        response = self.client.get(reverse('provider_instrument_search'), {'provider': 'tsetmc', 'q': 'test'})

        self.assertEqual(response.status_code, 200)
        self.assertJSONEqual(response.content, {'results': provider.search_instruments.return_value})
        search.get.assert_called_once_with('TSETMC')
        provider.search_instruments.assert_called_once_with('test')


class RahavardProviderTests(TestCase):
    @patch('portfolio.pricing.requests.get')
    def test_fetch_price_uses_real_close_price_and_converts_rial_to_toman(self, get):
        response = Mock()
        response.json.return_value = {'data': {
            'header_last_trade': {
                'real_close_price': 240110000,
                'end_date_time': '2026-09-20T18:19:00.083+03:30',
            },
            'last_trade': {'close_price': 1},
        }}
        get.return_value = response
        asset = Mock(provider_symbol='48179')

        result = RahavardProvider().fetch_price(asset)

        self.assertEqual(result.price, Decimal('24011000'))
        self.assertEqual(result.currency, 'IRT')
        self.assertEqual(result.provider, 'RAHAVARD')
        self.assertEqual(result.provider_symbol, '48179')
        get.assert_called_once_with(
            'https://rahavard365.com/api/v2/asset/48179',
            headers=RahavardProvider.request_headers, timeout=(3, 8),
        )

    @patch('portfolio.pricing.requests.get')
    def test_search_returns_listed_instruments_with_entity_ids(self, get):
        response = Mock()
        response.json.return_value = {'data': [
            {
                'entity_id': '2672', 'trade_symbol': 'طلا',
                'name': 'صندوق سرمایه گذاری پشتوانه چندکالایی پارسیان',
                'short_name': 'پشتوانه چندکالایی پارسیان', 'exchange': 'بورس کالا',
                'type': 'صندوق', 'unlisted_item': False,
            },
            {
                'entity_id': '632', 'trade_symbol': 'پردازش اطلاعات',
                'name': 'پردازش اطلاعات ایرانیان', 'short_name': 'پردازش اطلاعات ایرانیان',
                'exchange': 'فرابورس', 'type': 'سهام', 'unlisted_item': True,
            },
        ]}
        get.return_value = response

        results = RahavardProvider().search_instruments('پارسیان')

        self.assertEqual(results, [{
            'provider_symbol': '2672', 'asset_symbol': 'طلا', 'symbol': 'طلا',
            'name': 'پشتوانه چندکالایی پارسیان',
            'meta': 'بورس کالا · صندوق · Rahavard ID: 2672',
        }])
        get.assert_called_once_with(
            'https://rahavard365.com/api/v2/search', params={'keyword': 'پارسیان'},
            headers=RahavardProvider.request_headers, timeout=(3, 8),
        )

    @patch('portfolio.pricing.requests.get')
    def test_precious_metal_search_requires_an_empty_exchange_and_matching_name(self, get):
        response = Mock()
        response.json.return_value = {'data': [
            {'entity_id': '1', 'trade_symbol': 'طلا', 'name': 'صندوق طلای آزاد', 'short_name': 'صندوق طلای آزاد', 'exchange': '', 'type': 'صندوق', 'unlisted_item': False},
            {'entity_id': '2', 'trade_symbol': 'طلا', 'name': 'صندوق طلا', 'short_name': 'صندوق طلا', 'exchange': 'بورس کالا', 'type': 'صندوق', 'unlisted_item': False},
            {'entity_id': '3', 'trade_symbol': 'کاریزما', 'name': 'پردازش اطلاعات مالی کاریزما', 'short_name': 'کاریزما', 'exchange': '', 'type': 'سهام', 'unlisted_item': False},
            {'entity_id': '4', 'trade_symbol': 'طلا', 'name': 'طلای نقدی', 'short_name': 'طلای نقدی', 'exchange': '', 'type': 'کالا', 'unlisted_item': False},
        ]}
        get.return_value = response

        results = RahavardProvider().search_instruments('طلا', 'GOLD')

        self.assertEqual([result['provider_symbol'] for result in results], ['4', '1'])

    @patch('portfolio.pricing.requests.get')
    def test_search_handles_a_missing_data_list(self, get):
        response = Mock(); response.json.return_value = {}
        get.return_value = response

        with self.assertRaisesMessage(Exception, 'unexpected search response'):
            RahavardProvider().search_instruments('طلا')


class AbanTetherProviderTests(TestCase):
    def asset(self, provider_symbol='BTC'):
        return Asset.objects.create(
            name=provider_symbol, symbol=f'ABAN-{provider_symbol}', asset_type='US_STOCK', unit='UNIT',
            pricing_mode='MARKET', price_provider='ABANTETHER', provider_symbol=provider_symbol,
        )

    @patch('portfolio.pricing.requests.get')
    def test_fetch_price_uses_midpoint_of_public_buy_and_sell_quotes(self, get):
        response = Mock(); response.json.return_value = {'data': [
            {'symbol': 'BTC', 'name': 'Bitcoin', 'persian_name': 'بیت کوین', 'price_buy': '100.00', 'price_sell': '98.00', 'is_active': True},
        ]}; get.return_value = response

        result = AbanTetherProvider().fetch_price(self.asset())

        self.assertEqual(result.price, 99)
        self.assertEqual(result.currency, 'IRT')
        self.assertEqual(result.provider, 'ABANTETHER')
        get.assert_called_once_with(
            'https://api.abantether.com/api/v2/manager/coins', headers=AbanTetherProvider.request_headers,
            timeout=(3, 8),
        )

    @patch('portfolio.pricing.requests.get')
    def test_search_uses_abantether_persian_name(self, get):
        response = Mock(); response.json.return_value = {'data': [
            {'symbol': 'BTC', 'name': 'Bitcoin', 'persian_name': 'بیت کوین', 'price_buy': '100', 'price_sell': '99', 'is_active': True},
            {'symbol': 'ETH', 'name': 'Ethereum', 'persian_name': 'اتریوم', 'price_buy': '1', 'price_sell': '1', 'is_active': True},
        ]}; get.return_value = response

        results = AbanTetherProvider().search_instruments('بیت کوین')

        self.assertEqual(results, [{'provider_symbol': 'BTC', 'asset_symbol': 'BTC', 'symbol': 'BTC', 'name': 'بیت کوین', 'meta': 'Bitcoin'}])

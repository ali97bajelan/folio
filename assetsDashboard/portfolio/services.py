from decimal import Decimal
from django.db.models import Case, DecimalField, F, Q, Sum, When
from django.utils import timezone
from .models import Asset, AssetPrice, AssetValuation, Transaction

ZERO=Decimal('0')
def fmt(value, places=2):
    if value is None: return '—'
    return f'{value:,.{places}f}'

class HoldingService:
    @staticmethod
    def quantity_at(asset, moment=None):
        tx=asset.transactions.all()
        if moment: tx=tx.filter(executed_at__lte=moment)
        value=tx.aggregate(total=Sum(Case(When(transaction_type='BUY',then=F('quantity')),When(transaction_type='SELL',then=-F('quantity')),output_field=DecimalField(max_digits=28,decimal_places=10))))['total']
        return value or ZERO
    @staticmethod
    def by_location(asset):
        rows=[]
        for loc in list(asset.transactions.values_list('location',flat=True).distinct()):
            tx=asset.transactions.filter(location_id=loc)
            q=tx.aggregate(total=Sum(Case(When(transaction_type='BUY',then=F('quantity')),When(transaction_type='SELL',then=-F('quantity')),output_field=DecimalField(max_digits=28,decimal_places=10))))['total'] or ZERO
            if q: rows.append((asset.transactions.filter(location_id=loc).first().location if loc else None,q))
        return rows

class CostBasisService:
    @staticmethod
    def current(asset):
        quantity=ZERO; average=None
        for t in asset.transactions.order_by('executed_at','id'):
            if t.transaction_type=='BUY':
                cost=(t.quantity*(t.price_per_unit or ZERO))+(t.fee or ZERO)
                average=((quantity*(average or ZERO))+cost)/(quantity+t.quantity) if quantity+t.quantity else None
                quantity+=t.quantity
            else:
                quantity-=t.quantity
                if quantity==0: average=None
        return quantity, average

class PricingService:
    @staticmethod
    def latest(asset): return asset.prices.order_by('-captured_at').first()
    @staticmethod
    def price_at(asset,moment): return asset.prices.filter(captured_at__lte=moment).order_by('-captured_at').first() or asset.prices.order_by('captured_at').first()

class CurrencyConversionService:
    @staticmethod
    def usd_irt(moment=None):
        # Nobitex's USD proxy is normally the USDTIRT market. Accept common
        # user-facing aliases so existing assets do not require renaming.
        asset=Asset.objects.filter(
            Q(symbol__iexact='USD_IRT') | Q(symbol__iexact='USDTIRT') | Q(symbol__iexact='USDT_IRT') |
            Q(symbol__iexact='USDT') | Q(provider_symbol__iexact='USDTIRT') | Q(provider_symbol__iexact='USDT-RLS')
        ).first()
        if not asset: return None
        p=PricingService.price_at(asset,moment) if moment else PricingService.latest(asset)
        return p.price if p else None
    @classmethod
    def convert(cls,amount,source,target,moment=None):
        if source==target or (source in ('USD','USDT') and target in ('USD','USDT')): return amount
        rate=cls.usd_irt(moment)
        if not rate: return None
        if source=='IRT' and target in ('USD','USDT'): return amount/rate
        if source in ('USD','USDT') and target=='IRT': return amount*rate
        return None

class PortfolioValuationService:
    @classmethod
    def asset_value(cls,asset,currency='IRT',moment=None):
        q=HoldingService.quantity_at(asset,moment); p=PricingService.price_at(asset,moment) if moment else PricingService.latest(asset)
        if not p or not q: return ZERO if q==0 else None
        return CurrencyConversionService.convert(q*p.price,p.currency,currency,moment)
    @classmethod
    def total(cls,currency='IRT',moment=None):
        values=[cls.asset_value(a,currency,moment) for a in Asset.objects.filter(is_active=True)]
        return sum((v for v in values if v is not None),ZERO),sum(v is None for v in values)

    @classmethod
    def save_snapshots(cls, captured_at=None):
        """Persist each active holding's USDT and toman values at one instant."""
        captured_at = captured_at or timezone.now()
        for asset in Asset.objects.filter(is_active=True):
            AssetValuation.objects.create(
                asset=asset,
                quantity=HoldingService.quantity_at(asset),
                usdt_value=cls.asset_value(asset, 'USD'),
                toman_value=cls.asset_value(asset, 'IRT'),
                captured_at=captured_at,
            )

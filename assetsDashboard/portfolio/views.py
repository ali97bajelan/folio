from datetime import timedelta
from decimal import Decimal
from django.contrib import messages
from django.db import transaction as db_transaction
from django.db.models import Min, Q
from django.forms import formset_factory
from django.http import JsonResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone
from .i18n import translate
from .forms import AssetForm, LocationForm, OpeningHoldingForm, PriceForm, TagForm, TransactionForm
from .models import Asset, AssetPrice, Location, Tag, Transaction
from .services import CostBasisService, HoldingService, PortfolioValuationService, PricingService, fmt
from .pricing import PROVIDERS, PricingError, refresh_prices as run_price_refresh

def check_timeline(asset, candidate=None):
    quantity=Decimal('0')
    tx=list(asset.transactions.exclude(pk=candidate.pk if candidate and candidate.pk else None))
    if candidate: tx.append(candidate)
    for t in sorted(tx,key=lambda row:(row.executed_at,row.pk or 0)):
        quantity += t.quantity if t.transaction_type=='BUY' else -t.quantity
        if quantity < 0: return False
    return True

def enrich(asset,currency='IRT'):
    q,avg=CostBasisService.current(asset); p=PricingService.latest(asset); value=PortfolioValuationService.asset_value(asset,currency)
    price=p.price if p else None
    # Cost is meaningful only when its transaction currency matches price currency.
    pnl=(q*price-q*avg) if price is not None and avg is not None and (not asset.transactions.exclude(transaction_currency=p.currency).exists()) else None
    return {
        'asset':asset, 'quantity':q, 'price':price, 'price_currency':p.currency if p else None,
        'value':value, 'usdt_value':PortfolioValuationService.asset_value(asset, 'USD'),
        'toman_value':PortfolioValuationService.asset_value(asset, 'IRT'),
        'average':avg, 'pnl':pnl, 'updated':p.captured_at if p else None,
    }

TREND_RANGES = {
    'all': ('All time', None),
    '1y': ('1 year', timedelta(days=365)),
    '3m': ('3 months', timedelta(days=90)),
    '1m': ('1 month', timedelta(days=30)),
    '1w': ('1 week', timedelta(days=7)),
    '1d': ('1 day', timedelta(days=1)),
}

PORTFOLIO_CHANGE_PERIODS = (
    ('1 day', timedelta(days=1)),
    ('7 days', timedelta(days=7)),
    ('30 days', timedelta(days=30)),
)

def portfolio_changes(currency, current_total):
    """Return percentage changes in total portfolio value for dashboard summaries."""
    now = timezone.now()
    changes = []
    for label, duration in PORTFOLIO_CHANGE_PERIODS:
        previous_total, _ = PortfolioValuationService.total(currency, now - duration)
        percent = None if previous_total == 0 else (current_total - previous_total) / previous_total * Decimal('100')
        changes.append({
            'label': label,
            'percent': percent,
            'direction': 'up' if percent is not None and percent > 0 else 'down' if percent is not None and percent < 0 else 'flat',
        })
    return changes

def portfolio_history(currency, period):
    now = timezone.now()
    _, duration = TREND_RANGES[period]
    if duration is None:
        first_transaction = Transaction.objects.aggregate(first=Min('executed_at'))['first']
        first_price = AssetPrice.objects.aggregate(first=Min('captured_at'))['first']
        start = min((date for date in (first_transaction, first_price) if date), default=now)
    else:
        start = now - duration

    step = timedelta(hours=1) if period == '1d' else timedelta(days=1)
    label_format = '%H:%M' if period == '1d' else '%b %d'
    history = []
    moment = start
    while moment < now:
        value, _ = PortfolioValuationService.total(currency, moment)
        history.append({'label': moment.strftime(label_format), 'value': float(value)})
        moment += step
    value, _ = PortfolioValuationService.total(currency, now)
    history.append({'label': now.strftime(label_format), 'value': float(value)})
    return history

def dashboard(request):
    currency=request.GET.get('currency','IRT')
    if currency not in ('IRT','USD'): currency='IRT'
    assets=[enrich(a,currency) for a in Asset.objects.filter(is_active=True).prefetch_related('tags')]
    last_price_update = max((item['updated'] for item in assets if item['updated']), default=None)
    assets = sort_asset_rows(assets, 'value_desc')
    total,missing=PortfolioValuationService.total(currency)
    changes = portfolio_changes(currency, total)
    type_values={}
    for x in assets:
        if x['value'] is not None: type_values[x['asset'].get_asset_type_display()]=type_values.get(x['asset'].get_asset_type_display(),Decimal('0'))+x['value']
    selected=request.GET.get('tag')
    tags=Tag.objects.all(); tagged=[x for x in assets if selected and x['asset'].tags.filter(pk=selected).exists()]
    period=request.GET.get('trend','1m')
    if period not in TREND_RANGES: period='1m'
    history=portfolio_history(currency, period)
    trend_options=[(key, label) for key, (label, _) in TREND_RANGES.items()]
    chart_data = {
        'history': history,
        'type_labels': list(type_values),
        'type_values': [float(value) for value in type_values.values()],
    }
    return render(request,'portfolio/dashboard.html',{'assets':assets,'total':total,'currency':currency,'missing':missing,'last_price_update':last_price_update,'changes':changes,'type_values':type_values,'tags':tags,'selected':selected,'tagged':tagged,'history':history,'chart_data':chart_data,'trend_period':period,'trend_options':trend_options,'fmt':fmt})

def refresh_prices(request):
    if request.method == 'POST':
        outcomes=run_price_refresh()
        messages.info(request, ' · '.join(f'{name}: {state}' for name,state in outcomes) or 'No market providers configured.')
    return redirect('dashboard')

def provider_instrument_search(request):
    """Proxy provider instrument lookups so browser clients do not call providers directly."""
    query = request.GET.get('q', '').strip()
    provider_code = request.GET.get('provider', '').upper()
    if not query:
        return JsonResponse({'results': []})
    provider = PROVIDERS.get(provider_code)
    if provider is None:
        return JsonResponse({'results': [], 'error': 'Choose a supported market-price provider.'}, status=400)
    try:
        if provider_code == 'RAHAVARD':
            results = provider.search_instruments(query, request.GET.get('asset_type', ''))
        else:
            results = provider.search_instruments(query)
    except PricingError as exc:
        return JsonResponse({'results': [], 'error': str(exc)}, status=503)
    return JsonResponse({'results': results})

def tsetmc_instrument_search(request):
    """Backward-compatible endpoint for existing TSETMC search clients."""
    query = request.GET.get('q', '').strip()
    if not query:
        return JsonResponse({'results': []})
    try:
        results = PROVIDERS['TSETMC'].search_instruments(query)
    except PricingError as exc:
        return JsonResponse({'results': [], 'error': str(exc)}, status=503)
    return JsonResponse({'results': results})

ASSET_SORT_OPTIONS = {
    'value_desc': 'Value: high to low',
    'value_asc': 'Value: low to high',
    'name': 'Name: A–Z',
    'quantity_desc': 'Quantity: high to low',
}

def sort_asset_rows(assets, sort_by):
    if sort_by not in ASSET_SORT_OPTIONS:
        sort_by = 'value_desc'
    if sort_by.startswith('value_'):
        valued = [item for item in assets if item['toman_value'] is not None]
        missing = [item for item in assets if item['toman_value'] is None]
        return sorted(valued, key=lambda item: item['toman_value'], reverse=sort_by == 'value_desc') + missing
    if sort_by == 'quantity_desc':
        return sorted(assets, key=lambda item: item['quantity'], reverse=True)
    return sorted(assets, key=lambda item: item['asset'].name.casefold())

def asset_list(request):
    sort_by = request.GET.get('sort', 'value_desc')
    if sort_by not in ASSET_SORT_OPTIONS:
        sort_by = 'value_desc'
    assets = [enrich(asset, request.GET.get('currency', 'IRT')) for asset in Asset.objects.filter(is_active=True).prefetch_related('tags')]
    assets = sort_asset_rows(assets, sort_by)
    return render(request, 'portfolio/asset_list.html', {
        'assets': assets, 'fmt': fmt, 'sort_by': sort_by, 'sort_options': ASSET_SORT_OPTIONS.items(),
    })
def asset_detail(request,pk):
    asset=get_object_or_404(Asset,pk=pk); x=enrich(asset); locations=HoldingService.by_location(asset)
    return render(request,'portfolio/asset_detail.html',{'x':x,'locations':locations,'transactions':asset.transactions.select_related('location'),'fmt':fmt})
def asset_create(request):
    HoldingFormSet=formset_factory(OpeningHoldingForm, extra=1)
    form=AssetForm(request.POST or None); holdings=HoldingFormSet(request.POST or None, prefix='holdings')
    if request.method=='POST' and form.is_valid() and holdings.is_valid():
        with db_transaction.atomic():
            a=form.save()
            for holding in holdings.cleaned_data:
                if holding and holding.get('quantity') is not None:
                    Transaction.objects.create(asset=a,transaction_type='BUY',quantity=holding['quantity'],price_per_unit=holding.get('average_cost'),transaction_currency=a.manual_price_currency or 'IRT',location=holding['location'],executed_at=holding.get('acquired_at') or timezone.now(),notes='Initial position')
        messages.success(request,'Asset and opening holdings created.'); return redirect('asset_detail',a.pk)
    return render(request,'portfolio/form.html',{'form':form,'holdings_formset':holdings,'title':translate('Add asset'),'submit':translate('Create asset')})
def asset_edit(request,pk):
    a=get_object_or_404(Asset,pk=pk); form=AssetForm(request.POST or None,instance=a)
    if request.method=='POST' and form.is_valid(): form.save(); messages.success(request,'Asset updated.'); return redirect('asset_detail',pk)
    return render(request,'portfolio/form.html',{'form':form,'title':translate('Edit asset'),'submit':translate('Save changes')})
def asset_delete(request,pk):
    asset=get_object_or_404(Asset,pk=pk)
    if request.method=='POST':
        with db_transaction.atomic():
            asset.transactions.all().delete()
            asset.delete()
        messages.success(request, 'Asset deleted.')
        return redirect('asset_list')
    return render(request,'portfolio/confirm.html',{'object':asset,'label':'asset'})

def transaction_list(request): return render(request,'portfolio/transactions.html',{'transactions':Transaction.objects.select_related('asset','location')})
def transaction_create(request): return transaction_form(request)
def transaction_edit(request,pk): return transaction_form(request,get_object_or_404(Transaction,pk=pk))
def transaction_form(request,instance=None):
    form=TransactionForm(request.POST or None,instance=instance)
    if request.method=='POST' and form.is_valid():
        t=form.save(commit=False)
        if not check_timeline(t.asset,t):
            form.add_error('quantity','This change would create a negative holding at some point in the timeline.')
        else:
            t.save()
            messages.success(request,'Transaction saved.'); return redirect('asset_detail',t.asset.pk)
    return render(request,'portfolio/form.html',{'form':form,'title':translate('Edit transaction') if instance else translate('Record transaction'),'submit':translate('Save transaction')})
def transaction_delete(request,pk):
    t=get_object_or_404(Transaction,pk=pk)
    if request.method=='POST': t.delete(); messages.success(request,'Transaction deleted.'); return redirect('transaction_list')
    return render(request,'portfolio/confirm.html',{'object':t,'label':'transaction'})
def manual_price(request,pk):
    asset=get_object_or_404(Asset,pk=pk); form=PriceForm(request.POST or None)
    if request.method=='POST' and form.is_valid():
        p=form.save(commit=False); p.asset=asset; p.provider='MANUAL'; p.provider_symbol=asset.symbol; p.save(); PortfolioValuationService.save_snapshots(); messages.success(request,'Price and valuation snapshots recorded.'); return redirect('asset_detail',pk)
    return render(request,'portfolio/form.html',{'form':form,'title':f"{translate('Update price')} {asset.symbol}",'submit':translate('Save price')})
def tags(request):
    form=TagForm(request.POST or None)
    if request.method=='POST' and form.is_valid(): form.save(); return redirect('tags')
    return render(request,'portfolio/simple_list.html',{'title':translate('Tags'),'form':form,'items':Tag.objects.all()})
def locations(request):
    form=LocationForm(request.POST or None)
    if request.method=='POST' and form.is_valid(): form.save(); return redirect('locations')
    return render(request,'portfolio/simple_list.html',{'title':translate('Locations'),'form':form,'items':Location.objects.all()})

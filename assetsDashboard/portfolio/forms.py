from django import forms
from django.utils import timezone
from .models import Asset, AssetPrice, Location, Tag, Transaction
from .i18n import translate

class StyledForm(forms.ModelForm):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        for f in self.fields.values():
            f.widget.attrs['class']='field'
            f.label = translate(f.label)

class AssetForm(StyledForm):
    class Meta:
        model=Asset; fields=['name','symbol','asset_type','unit','pricing_mode','price_provider','provider_symbol','manual_price_currency','tags','is_active']
        widgets={'tags':forms.CheckboxSelectMultiple}
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        for field_name in ('symbol', 'provider_symbol'):
            self.fields[field_name].required = False
            self.fields[field_name].widget = forms.HiddenInput()
        self.fields['price_provider'].help_text = 'Matching provider assets will be suggested from the asset name after you select a provider.'
    def clean(self):
        data=super().clean(); q=data.get('initial_quantity'); c=data.get('initial_average_cost')
        if c and not q: self.add_error('initial_quantity','Enter an initial quantity with an average cost.')
        mode=data.get('pricing_mode'); provider=data.get('price_provider'); symbol=data.get('provider_symbol')
        if not data.get('symbol') and data.get('name'):
            data['symbol'] = data['name'].strip().upper().replace(' ', '-')[:50]
        if mode == Asset.Pricing.MARKET and not provider: self.add_error('price_provider','Choose a market-price provider.')
        if mode == Asset.Pricing.MARKET and provider == Asset.Provider.MANUAL: self.add_error('price_provider','Choose Manual pricing mode for manual price entry.')
        if mode == Asset.Pricing.MARKET and provider and not symbol: self.add_error('provider_symbol','Choose one of the suggested provider markets.')
        return data

class OpeningHoldingForm(forms.Form):
    location=forms.ModelChoiceField(queryset=Location.objects.all(), required=False)
    quantity=forms.DecimalField(required=False, max_digits=28, decimal_places=10)
    average_cost=forms.DecimalField(required=False, max_digits=28, decimal_places=10)
    acquired_at=forms.DateTimeField(required=False, widget=forms.DateTimeInput(attrs={'type':'datetime-local'}))
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        for field in self.fields.values():
            field.widget.attrs['class']='field'
            field.label = translate(field.label)
    def clean(self):
        data=super().clean(); location=data.get('location'); quantity=data.get('quantity'); cost=data.get('average_cost'); acquired_at=data.get('acquired_at')
        if quantity is None:
            if location or cost is not None or acquired_at: self.add_error('quantity', 'Enter a quantity for this opening holding.')
        else:
            if quantity <= 0: self.add_error('quantity', 'Quantity must be greater than zero.')
            if not location: self.add_error('location', 'Choose where this holding is stored.')
        return data

class TransactionForm(StyledForm):
    class Meta:
        model=Transaction; fields=['asset','transaction_type','quantity','price_per_unit','fee','transaction_currency','location','executed_at','notes']
        widgets={'executed_at':forms.DateTimeInput(attrs={'type':'datetime-local'}),'notes':forms.Textarea(attrs={'rows':3})}
    def clean_executed_at(self): return self.cleaned_data['executed_at']

class PriceForm(StyledForm):
    class Meta:
        model=AssetPrice; fields=['price','currency','captured_at']
        widgets={'captured_at':forms.DateTimeInput(attrs={'type':'datetime-local'})}
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        if not self.initial.get('captured_at'): self.initial['captured_at']=timezone.localtime().strftime('%Y-%m-%dT%H:%M')

class TagForm(StyledForm):
    class Meta: model=Tag; fields=['name']
class LocationForm(StyledForm):
    class Meta: model=Location; fields=['name','notes']

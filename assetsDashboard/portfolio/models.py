from django.db import models

class Tag(models.Model):
    name = models.CharField(max_length=100, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    class Meta: ordering = ['name']
    def __str__(self): return self.name

class Location(models.Model):
    name = models.CharField(max_length=150, unique=True)
    notes = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    class Meta: ordering = ['name']
    def __str__(self): return self.name

class Asset(models.Model):
    class Type(models.TextChoices):
        FIXED_INCOME='FIXED_INCOME','Fixed income'; USD='USD','USD'; CRYPTO='CRYPTO','Crypto'; IRAN_STOCK='IRAN_STOCK','Iran stock'; US_STOCK='US_STOCK','Global stock'; GOLD='GOLD','Gold'; SILVER='SILVER','Silver'; MANUAL='MANUAL','Manual'
    class Unit(models.TextChoices): UNIT='UNIT','Unit'; GRAM='GRAM','Gram'
    class Pricing(models.TextChoices): MARKET='MARKET','Market'; MANUAL='MANUAL','Manual'
    class Provider(models.TextChoices):
        NOBITEX='NOBITEX','Nobitex — Crypto markets'
        ABANTETHER='ABANTETHER','Aban Tether — Crypto assets'
        TSETMC='TSETMC','TSETMC — Iran stock market'
        RAHAVARD='RAHAVARD','Rahavard 365 — Iran markets'
        MANUAL='MANUAL','Manual price entry'
    name=models.CharField(max_length=200); symbol=models.CharField(max_length=50, unique=True)
    asset_type=models.CharField(max_length=30, choices=Type.choices); unit=models.CharField(max_length=20, choices=Unit.choices, default=Unit.UNIT)
    pricing_mode=models.CharField(max_length=20, choices=Pricing.choices, default=Pricing.MARKET)
    price_provider=models.CharField(max_length=50, choices=Provider.choices, blank=True); provider_symbol=models.CharField(max_length=100, blank=True)
    manual_price_currency=models.CharField(max_length=10, blank=True, default='IRT'); tags=models.ManyToManyField(Tag, blank=True)
    is_active=models.BooleanField(default=True); created_at=models.DateTimeField(auto_now_add=True); updated_at=models.DateTimeField(auto_now=True)
    class Meta: ordering=['name']
    def __str__(self): return f'{self.symbol} · {self.name}'

class Transaction(models.Model):
    class Kind(models.TextChoices): BUY='BUY','Buy'; SELL='SELL','Sell'
    asset=models.ForeignKey(Asset,on_delete=models.PROTECT, related_name='transactions'); transaction_type=models.CharField(max_length=10,choices=Kind.choices)
    quantity=models.DecimalField(max_digits=28,decimal_places=10); price_per_unit=models.DecimalField(max_digits=28,decimal_places=10,null=True,blank=True)
    fee=models.DecimalField(max_digits=28,decimal_places=10,null=True,blank=True); transaction_currency=models.CharField(max_length=10,blank=True,default='IRT')
    location=models.ForeignKey(Location,on_delete=models.PROTECT,null=True,blank=True); executed_at=models.DateTimeField(); notes=models.TextField(blank=True)
    created_at=models.DateTimeField(auto_now_add=True); updated_at=models.DateTimeField(auto_now=True)
    class Meta: ordering=['executed_at','id']; indexes=[models.Index(fields=['asset','executed_at']),models.Index(fields=['executed_at'])]

class AssetPrice(models.Model):
    asset=models.ForeignKey(Asset,on_delete=models.CASCADE,related_name='prices'); price=models.DecimalField(max_digits=28,decimal_places=10); currency=models.CharField(max_length=10)
    provider=models.CharField(max_length=50); provider_symbol=models.CharField(max_length=100,blank=True); captured_at=models.DateTimeField(); created_at=models.DateTimeField(auto_now_add=True)
    class Meta: ordering=['-captured_at']; indexes=[models.Index(fields=['asset','captured_at']),models.Index(fields=['provider','captured_at'])]

class AssetValuation(models.Model):
    """Saved holding values in the two portfolio reporting currencies."""
    asset=models.ForeignKey(Asset,on_delete=models.CASCADE,related_name='valuations')
    quantity=models.DecimalField(max_digits=28,decimal_places=10)
    usdt_value=models.DecimalField(max_digits=28,decimal_places=10,null=True,blank=True)
    toman_value=models.DecimalField(max_digits=28,decimal_places=10,null=True,blank=True)
    captured_at=models.DateTimeField()
    created_at=models.DateTimeField(auto_now_add=True)
    class Meta:
        ordering=['-captured_at']
        indexes=[models.Index(fields=['asset','captured_at'])]

class ProviderRefresh(models.Model):
    class Status(models.TextChoices): SUCCESS='SUCCESS','Success'; PARTIAL='PARTIAL','Partial'; FAILED='FAILED','Failed'
    provider=models.CharField(max_length=50); started_at=models.DateTimeField(); finished_at=models.DateTimeField(null=True,blank=True); status=models.CharField(max_length=10,choices=Status.choices); error_message=models.TextField(blank=True)
    class Meta: ordering=['-started_at']

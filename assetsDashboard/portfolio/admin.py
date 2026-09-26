from django.contrib import admin
from .models import Asset, AssetPrice, AssetValuation, Location, ProviderRefresh, Tag, Transaction
admin.site.register([Asset, AssetPrice, AssetValuation, Location, ProviderRefresh, Tag, Transaction])

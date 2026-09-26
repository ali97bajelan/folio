from django.db import migrations

def normalize(apps, schema_editor):
    Asset = apps.get_model('portfolio', 'Asset')
    for asset in Asset.objects.exclude(price_provider=''):
        normalized = asset.price_provider.upper()
        if normalized != asset.price_provider:
            asset.price_provider = normalized
            asset.save(update_fields=['price_provider'])

class Migration(migrations.Migration):
    dependencies = [('portfolio', '0003_asset_provider_choices')]
    operations = [migrations.RunPython(normalize, migrations.RunPython.noop)]

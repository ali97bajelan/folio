from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [('portfolio', '0008_update_abantether_provider_label')]

    operations = [
        migrations.AlterField(
            model_name='asset', name='asset_type',
            field=models.CharField(max_length=30, choices=[
                ('FIXED_INCOME', 'Fixed income'), ('USD', 'USD'), ('CRYPTO', 'Crypto'),
                ('IRAN_STOCK', 'Iran stock'), ('US_STOCK', 'Global stock'), ('GOLD', 'Gold'),
                ('SILVER', 'Silver'), ('MANUAL', 'Manual'),
            ]),
        ),
    ]

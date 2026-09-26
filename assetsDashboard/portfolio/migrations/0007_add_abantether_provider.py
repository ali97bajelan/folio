from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [('portfolio', '0006_add_tsetmc_provider')]

    operations = [
        migrations.AlterField(
            model_name='asset', name='price_provider',
            field=models.CharField(blank=True, max_length=50, choices=[
                ('NOBITEX', 'Nobitex — Crypto markets'),
                ('ABANTETHER', 'Aban Tether — Crypto & global markets'),
                ('TSETMC', 'TSETMC — Iran stock market'),
                ('RAHAVARD', 'Rahavard 365 — Iran markets'),
                ('MANUAL', 'Manual price entry'),
            ]),
        ),
    ]

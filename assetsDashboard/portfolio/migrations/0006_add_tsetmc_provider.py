from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [('portfolio', '0005_assetvaluation')]

    operations = [
        migrations.AlterField(
            model_name='asset',
            name='price_provider',
            field=models.CharField(
                blank=True,
                choices=[
                    ('NOBITEX', 'Nobitex — Crypto markets'),
                    ('TSETMC', 'TSETMC — Iran stock market'),
                    ('RAHAVARD', 'Rahavard 365 — Iran markets'),
                    ('MANUAL', 'Manual price entry'),
                ],
                max_length=50,
            ),
        ),
    ]

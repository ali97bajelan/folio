from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [('portfolio', '0004_normalize_provider_values')]

    operations = [
        migrations.CreateModel(
            name='AssetValuation',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('quantity', models.DecimalField(decimal_places=10, max_digits=28)),
                ('usdt_value', models.DecimalField(blank=True, decimal_places=10, max_digits=28, null=True)),
                ('toman_value', models.DecimalField(blank=True, decimal_places=10, max_digits=28, null=True)),
                ('captured_at', models.DateTimeField()),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('asset', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='valuations', to='portfolio.asset')),
            ],
            options={'ordering': ['-captured_at']},
        ),
        migrations.AddIndex(
            model_name='assetvaluation',
            index=models.Index(fields=['asset', 'captured_at'], name='portfolio_a_asset_i_a823f4_idx'),
        ),
    ]

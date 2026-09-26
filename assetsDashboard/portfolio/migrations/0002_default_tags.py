from django.db import migrations

def add_default_tags(apps, schema_editor):
    Tag = apps.get_model('portfolio', 'Tag')
    Tag.objects.get_or_create(name='ریالی')
    Tag.objects.get_or_create(name='دلاری')

class Migration(migrations.Migration):
    dependencies = [('portfolio', '0001_initial')]
    operations = [migrations.RunPython(add_default_tags, migrations.RunPython.noop)]

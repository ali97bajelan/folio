from django import template
from portfolio.i18n import translate

register = template.Library()


@register.filter
def ui(value):
    return translate(value)

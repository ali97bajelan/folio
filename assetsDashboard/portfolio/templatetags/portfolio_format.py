from decimal import Decimal, InvalidOperation, ROUND_DOWN

from django import template


register = template.Library()


@register.filter
def irt_compact(value):
    """Format toman values with one decimal place in millions or billions."""
    if value is None:
        return 'Unavailable'

    try:
        amount = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        return value

    for divisor, suffix in ((Decimal('1000000000'), 'B'), (Decimal('1000000'), 'M')):
        if abs(amount) >= divisor:
            compact = (amount / divisor).quantize(Decimal('0.1'), rounding=ROUND_DOWN)
            return f'{compact.normalize():f}{suffix}'

    return f'{amount:,.0f}'


@register.filter
def irt_whole(value):
    """Show a toman price without fractions, truncating rather than rounding."""
    if value is None:
        return 'Unavailable'

    try:
        amount = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        return value

    return f'{amount.quantize(Decimal("1"), rounding=ROUND_DOWN):,}'


@register.filter
def decimal_trim(value):
    """Format stored decimal values without insignificant trailing zeroes."""
    if value is None:
        return 'Unavailable'

    try:
        amount = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        return value

    return f'{amount:,.10f}'.rstrip('0').rstrip('.')


@register.filter
def quantity_with_unit(value, unit):
    """Append ``g`` to gram-denominated quantities without changing other units."""
    quantity = decimal_trim(value)
    return f'{quantity} g' if unit == 'GRAM' and quantity != 'Unavailable' else quantity

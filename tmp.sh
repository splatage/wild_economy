awk '
function round_price_text(s,    x) {
    x = s + 0

    if (x < 10) {
        return s
    } else if (x < 100) {
        return int(x + 0.5)
    } else {
        return int((x + 2.5) / 5) * 5
    }
}
{
    if ($0 ~ /[0-9]+(\.[0-9]+)?[[:space:]]*$/) {
        original = $0

        price = $0
        sub(/^.*:[[:space:]]*/, "", price)

        new = round_price_text(price)

        prefix = original
        sub(/[0-9]+(\.[0-9]+)?[[:space:]]*$/, "", prefix)

        printf "%s%s\n", prefix, new
    } else {
        print
    }
}
'

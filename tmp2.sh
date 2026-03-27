awk '
BEGIN {
    in_items = 0
    n = 0
}
function flush_items(   i, j, tmp) {
    for (i = 1; i <= n; i++) {
        for (j = i + 1; j <= n; j++) {
            if (keys[i] > keys[j]) {
                tmp = keys[i]; keys[i] = keys[j]; keys[j] = tmp
                tmp = lines[i]; lines[i] = lines[j]; lines[j] = tmp
            }
        }
    }
    for (i = 1; i <= n; i++) {
        print lines[i]
    }
    n = 0
}
{
    if ($0 ~ /^items:[[:space:]]*$/) {
        if (in_items) flush_items()
        in_items = 1
        print
        next
    }

    if (in_items) {
        if ($0 ~ /^  [^[:space:]][^:]*:[[:space:]]*/) {
            lines[++n] = $0
            key = $0
            sub(/^  /, "", key)
            sub(/:.*/, "", key)
            keys[n] = key
            next
        } else {
            flush_items()
            in_items = 0
        }
    }

    print
}
END {
    if (in_items) flush_items()
}
'

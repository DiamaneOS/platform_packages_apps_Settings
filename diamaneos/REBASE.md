# Rebase checks

## Homepage copies

The Tally homepage loads its own copies of three stock files. Changes GrapheneOS makes to the stock
files do not reach the copies by themselves.

| Stock file | Our copy |
| --- | --- |
| `res/xml/top_level_settings_expressive.xml` | `res/xml/tally_top_level_settings.xml` |
| `res/layout/settings_homepage_container.xml` | `res/layout/tally_settings_homepage_container.xml` |
| `res/layout/search_bar.xml` | `res/layout/tally_search_bar.xml` |

At each rebase, list what changed in the stock files between the old and the new GrapheneOS tag:

```
git diff <old tag> <new tag> -- res/xml/top_level_settings_expressive.xml \
    res/layout/settings_homepage_container.xml res/layout/search_bar.xml
```

- Take every change into the matching copy, unless the copy replaces that part on purpose.
- An empty diff means the copies need nothing.

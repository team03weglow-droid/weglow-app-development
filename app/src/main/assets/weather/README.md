# Country weather defaults

Source: https://github.com/restcountries/restcountries/blob/master/src/main/resources/countriesV3.1.json
Downloaded 2026-11-05. Derived fields: country code/name, first capital city and
capital coordinates. License: Mozilla Public License 2.0, included alongside the
catalog. Sri Lanka is intentionally overridden to Colombo (6.9271, 79.8612), the
product's selected main city. Other entries use the source's first capital.

The catalog is bundled; no country-lookup service is contacted at runtime.
Network country takes precedence over SIM country, then the phone region.
Unknown countries do not silently select an unrelated country or coordinates.

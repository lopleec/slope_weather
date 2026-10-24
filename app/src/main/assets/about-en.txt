Slope Weather
Package: com.lopleec.slopeweather
A native Android weather application with forecasts, weather maps, air quality and astronomical calculations.

Repository: https://github.com/lopleec/slope_weather
Copyright 2026 Lopleec
Licensed under the Apache License, Version 2.0
The complete application-source license is bundled and available under Project license. Third-party licenses apply to their respective components.

Data integrity and offline use
Only returned source values are displayed. A missing value is not zero. Network failures retain the last successfully retrieved weather bundle, with its retrieval and source times; without a cache the app reports the failure. Forecast model values, station observations, derived dew point and astronomical calculations are different products. Optional Open-Meteo supplementation fills missing fields and is credited on the weather page; it does not turn model output into a station observation. Native forecast intervals are preserved. Rain animations illustrate the selected weather and are not measurements.

Forecasts and observations
Open-Meteo: https://open-meteo.com/ and https://open-meteo.com/en/docs
Also supplies coordinate time-zone metadata for the direct MET source. Best match and selectable CMA GRAPES, JMA GSM/MSM, DWD ICON, ECMWF IFS, NOAA GFS, Météo-France ARPEGE/AROME, UK Met Office and KNMI HARMONIE products. Geographic extent, forecast horizon, variables and update times depend on the upstream model. The free endpoint is for noncommercial use and has rate limits. Attribution and the applicable upstream licence, including CC BY 4.0 and UKMO CC BY-SA 4.0 where specified by Open-Meteo, must be preserved. The app is not licensed by any national service merely because an endpoint is public.

NMC / 中国中央气象台: https://www.nmc.cn/
Mainland station observations, daily forecasts, native three-hour website forecast tables, station AQI and official warnings. Public website interfaces are undocumented and may change. Station values can differ from conditions at the requested coordinate. Public access is not permission for unrestricted redistribution; consult the site's terms before distributing a product.

JMA / Japan Meteorological Agency: https://www.jma.go.jp/ and https://www.jma.go.jp/jma/en/copyright.html
AMeDAS observations, regional and weekly forecasts, warnings and Japan precipitation radar. Station/region lookup files were obtained from JMA. Follow the Government of Japan Standard Terms of Use and the JMA attribution and third-party exceptions. Nearest-station and regional forecasts are labelled source products, not precise measurements at every point.

Bright Sky / DWD: https://brightsky.dev/ and https://opendata.dwd.de/
German station forecasts and warnings. DWD source terms and attribution apply. MET Norway Locationforecast: https://api.met.no/ and https://api.met.no/doc/TermsOfService — CC BY 4.0, caching and identifiable User-Agent requirements. The app sends only the contact email or website entered by the user; no third-party contact identity is invented.

Hong Kong Observatory: https://www.hko.gov.hk/en/abouthko/opendata_intro.htm
Hong Kong current observations, nine-day forecasts and warnings; HKO open-data attribution and conditions apply. NOAA / US National Weather Service: https://www.weather.gov/documentation/services-web-api — US hourly/grid forecasts and alerts. Follow NOAA/NWS reuse and attribution guidance and any third-party notices.

Air quality and pollen
CAMS via Open-Meteo: https://open-meteo.com/en/docs/air-quality-api and https://atmosphere.copernicus.eu/
Contains modified Copernicus Atmosphere Monitoring Service information, distributed by Open-Meteo. Modelled concentrations are displayed in μg/m³; pollen in grains/m³. These are grid estimates, not local sensor readings. Pollen is only provided in supported regions and seasons.
US AQI and European AQI are independent indices provided by the API. The European scale uses its own 0–20/20–40/40–60/60–80/80–100/>100 bands. China AQI is shown only when a source supplies cn_aqi, currently NMC; it is not produced by relabelling another index. If the selected standard is unavailable the app says so. Different averaging periods and pollutant thresholds prevent simple numerical conversion. Display rounding does not imply extra measurement precision.

Official weather warnings
WMO Severe Weather Information Centre: https://severeweather.wmo.int/ and https://severeweather.wmo.int/note.html
SWIC aggregates CAP warnings issued by over 130 national meteorological/hydrological services. The point-intersection service is queried globally, alongside direct NMC, JMA, HKO, NWS and DWD/Bright Sky sources where available. WMO is the distributor; the original issuing authority, source text, expiry and official link are retained. Test/cancelled/expired CAP messages are excluded. An empty result is not evidence that a country has no warnings: coverage, CAP participation, delayed feeds and network failures all matter. Consult the issuing authority for safety decisions. Background checks run about every three hours for the current and saved places when enabled; Android scheduling and notification permissions can delay or prevent delivery.

Precipitation maps
RainViewer: https://www.rainviewer.com/api/weather-maps-api.html
Published composite radar frames cover the past two hours at ten-minute intervals, maximum native tile zoom 7. Frame generation time is not the exact observation time of every contributing radar. Coverage varies; the coverage mask shades areas without radar coverage. No echo does not prove no rain. No future radar frames or invented long-history frames are generated. API attribution and service terms apply.
JMA radar: https://www.jma.go.jp/bosai/nowc/ — only timestamps published by the Japanese source are played, within regional coverage.
NASA GIBS / GPM IMERG: https://nasa-gibs.github.io/gibs-api-docs/ and https://gpm.nasa.gov/data/imerg
Global satellite precipitation-rate ESTIMATES, not live radar. Half-hour frames are selected only from the service's advertised availability, up to 24 hours ending at the latest published time. Delays of several hours and missing areas are possible. The official NASA GPM colour legend is bundled unchanged. NASA/EOSDIS scientific data and imagery reuse policies and applicable source credits apply. The MODIS Terra true-colour base image is dated separately; clouds and gaps may obscure terrain. A smooth transition between actual image frames does not create intermediate precipitation observations.

Maps and place names
© OpenStreetMap contributors, ODbL: https://www.openstreetmap.org/copyright
Leaflet renders the interactive base map. Standard tiles: https://tile.openstreetmap.org/ — respect https://operations.osmfoundation.org/policies/tiles/ including visible attribution, an identifiable User-Agent, HTTP caching and no bulk/offline scraping. Only visible tiles are requested. If the base tiles fail, the app tries the Esri World Street Map service; its on-map attribution and source rights remain in force: https://www.esri.com/en-us/legal/terms/full-master-agreement
Photon / komoot: https://github.com/komoot/photon — OSM-derived search and reverse lookup. The public demo has no availability guarantee and may throttle requests. Search is debounced and cached; English/German/French and genuine local names are available, while other app languages also use GeoNames/Open-Meteo translations and Android Geocoder. No place name is transliterated or invented by this app. District-level coverage and translations vary. Multiple similarly named administrative places may be returned. Photon can be replaced with a self-hosted service for a larger deployment.
Open-Meteo geocoding uses GeoNames: https://www.geonames.org/ — CC BY 4.0 attribution applies. Android Geocoder is provided by the device/platform vendor. A custom reverseGeocoderUrl may point to a separately configured Nominatim deployment; the public Nominatim service is not used for autocomplete. Its policy is https://operations.osmfoundation.org/policies/nominatim/ and requires an informed operator decision, caching and strict rate limits.
Alibaba DataV GeoAtlas: https://datav.aliyun.com/portal/school/atlas/area_selector — mainland administrative boundaries, bundled province lookup and cached city/district GeoJSON. Weather requests keep WGS84 coordinates; only boundary containment and AMap/Baidu external links apply the required GCJ-02/BD-09 transformations. Consult DataV terms before redistribution.
Google Maps, AMap, Baidu Maps, OpenStreetMap and Windy links open the corresponding external service. Their proprietary SDKs and credentials are not bundled. External apps have their own terms and privacy policies.

Astronomy
commons-suncalc 3.11: https://shredzone.org/maven/commons-suncalc/
The decorative sky follows the calculated solar position at the selected place and the current instant, including dawn/dusk and polar day/night; it does not follow the phone UI theme or a stale weather timestamp. Card surfaces and detail pages independently follow the app/system appearance setting. Cloud shapes are a stylized visual effect, not satellite imagery.

Moon phase, illuminated fraction, moonrise/set, sunrise/set and civil twilight are calculated locally for the place's coordinates, date and time zone. The moon graphic is a schematic illumination view, not an observed photograph or sky orientation. Clouds, terrain and refraction affect actual visibility. Dew point is calculated with the Magnus approximation only when valid temperature and humidity are available. These calculations work offline and are identified as calculations. USNO data was used as an independent test reference, not as a live app dependency: https://aa.usno.navy.mil/data/api

Privacy and storage
The app has no advertising, account or analytics SDK. Coordinates are sent to the selected weather source, optional supplemental and air-quality services, warning services and geocoders when those features run. Search words go to search providers. A viewed map sends tile coordinates, IP address and an identifiable app User-Agent to the tile service. Selected places, settings, weather responses and HTTP cache are stored in the app's private storage. Location and notification permissions are optional and managed by Android. No background location permission is requested. Uninstalling the application removes its private data.

Open-source components
AndroidX Activity, Core, Compose UI/Foundation/Runtime/Material 3/Material Icons, Lifecycle, WorkManager and their AndroidX dependencies: Apache-2.0, https://android.googlesource.com/platform/frameworks/support/
Kotlin, kotlinx.coroutines and required Kotlin/JetBrains dependencies: Apache-2.0 and the notices shipped by each dependency, https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines
commons-suncalc 3.11: Apache-2.0. jsoup 1.23.1: MIT, https://jsoup.org/license
Leaflet 1.9.4: BSD-2-Clause, copyright Vladimir Agafonkin and CloudMade, https://leafletjs.com/
Full applicable license texts and the resolved runtime dependency inventory are available in Open-source licenses. Build-only tools and test libraries are not part of the installed application. The application source is licensed under Apache-2.0; third-party licences apply to their corresponding components. Provider trademarks and data rights remain with their respective owners.

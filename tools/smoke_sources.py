#!/usr/bin/env python3
"""Read-only live probes. No API keys; one request per service/model. Python 3 stdlib."""
import concurrent.futures, datetime, json, pathlib, urllib.request, urllib.parse, sys, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'docs'/'live-probes'
OUT.mkdir(exist_ok=True)
fields=['temperature_2m','relative_humidity_2m','dew_point_2m','apparent_temperature','precipitation_probability','precipitation','rain','snowfall','weather_code','pressure_msl','surface_pressure','cloud_cover','visibility','wind_speed_10m','wind_direction_10m','wind_gusts_10m','uv_index','is_day','shortwave_radiation','cape']
basic=[x for x in fields if x not in ['precipitation_probability','uv_index','shortwave_radiation','cape']]
day='weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,wind_speed_10m_max'
urls={}
for name,endpoint,days in [('auto','forecast',14),('cma','cma',7),('jma-model','jma',7),('dwd','dwd-icon',7),('ecmwf','ecmwf',10),('gfs','gfs',10),('france','meteofrance',4),('ukmo','forecast',7),('knmi','forecast',2)]:
    lat,lon={'knmi':(52.37,4.90),'jma-model':(35.6762,139.6503),'cma':(39.9,116.4),'dwd':(52.52,13.4),'france':(48.8566,2.3522),'ukmo':(51.5074,-.1278)}.get(name,(31.23,121.47))
    q=dict(latitude=lat,longitude=lon,hourly=','.join(fields),daily=day+',uv_index_max,precipitation_probability_max',timezone='auto',timeformat='unixtime',forecast_days=days)
    if name in ['ukmo','knmi']:q['models']=name+'_seamless'
    if name=='auto':q.update(current=','.join(fields),minutely_15='precipitation',forecast_minutely_15=24)
    urls[name]='https://api.open-meteo.com/v1/'+endpoint+'?'+urllib.parse.urlencode(q)
urls.update({
'nmc':'https://www.nmc.cn/rest/weather?stationid=WwcJd',
'nmc-search':'https://www.nmc.cn/essearch/api/autocomplete?q=shanghai',
'hko-current':'https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=rhrread&lang=en',
'hko-daily':'https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=fnd&lang=en',
'hko-alerts':'https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=warningInfo&lang=en',
'jma-official':'https://www.jma.go.jp/bosai/forecast/data/forecast/130000.json',
'jma-warnings':'https://www.jma.go.jp/bosai/warning/data/r8/130000.json',
'china-administrative':'https://geo.datav.aliyun.com/areas_v3/bound/110000_full.json',
'jma-radar':'https://www.jma.go.jp/bosai/jmatile/data/nowc/targetTimes_N1.json',
'brightsky':'https://api.brightsky.dev/weather?lat=52.52&lon=13.4&date='+datetime.date.today().isoformat()+'&last_date='+(datetime.date.today()+datetime.timedelta(days=9)).isoformat(),
'nws-points':'https://api.weather.gov/points/40.7128,-74.0060',
'rainviewer':'https://api.rainviewer.com/public/weather-maps.json',
'air-quality':'https://air-quality-api.open-meteo.com/v1/air-quality?latitude=31.23&longitude=121.47&current=us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,uv_index,grass_pollen,birch_pollen,alder_pollen,ragweed_pollen,mugwort_pollen,olive_pollen&timeformat=unixtime'})
def fetch(item):
    name,url=item
    try:
        response=subprocess.run(['curl','-L','--fail-with-body','--max-time','40','-sS','-A','Slope Weather/0.1 (Android; com.lopleec.slopeweather)','-H','Accept: application/json','-w','\n%{http_code}',url],capture_output=True)
        raw,status=response.stdout.rsplit(b'\n',1);status=int(status)
        if response.returncode: raise RuntimeError(str(status)+': '+raw[:300].decode(errors='replace'))
        j=json.loads(raw);(OUT/(name+'.json')).write_bytes(raw)
        details={}
        if name in ['auto','cma','jma-model','dwd','ecmwf','gfs','france','ukmo','knmi']:
            details['hours']=len(j.get('hourly',{}).get('time',[]))
            details['nonnull_fields']={k:sum(x is not None for x in v) for k,v in j.get('hourly',{}).items() if k!='time'}
            details['temperature_values']=sum(x is not None for x in j.get('hourly',{}).get('temperature_2m',[]))
            details['first_time']=j.get('hourly',{}).get('time',[None])[0]
        return dict(name=name,http=status,ok=True,**details)
    except Exception as e:return dict(name=name,ok=False,error=str(e))
if sys.argv[1:]: urls={k:v for k,v in urls.items() if k in sys.argv[1:]}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: results=list(pool.map(fetch,urls.items()))
previous=json.loads((OUT/'report.json').read_text()).get('results',[]) if sys.argv[1:] and (OUT/'report.json').exists() else []
results=sorted({r['name']:r for r in previous+results}.values(),key=lambda r:r['name'])
report=dict(checked_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),results=results)
(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
for r in results:print(json.dumps(r,ensure_ascii=False),flush=True)
# Fail only for the default provider; optional upstream failures are captured in the report.
sys.exit(0 if next((r for r in results if r['name']=='auto'),{'ok':True})['ok'] else 1)

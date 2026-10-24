package com.lopleec.slopeweather.data

import kotlin.math.*

/** Convert a WGS84 device fix only when querying a GCJ-02 boundary or map. Weather keeps WGS84. */
fun wgs84ToGcj02(lat: Double,lon: Double): Pair<Double,Double> {
    if(lon !in 72.004..137.8347 || lat !in .8293..55.8271) return lat to lon
    val x=lon-105;val y=lat-35
    var dLat=-100+2*x+3*y+.2*y*y+.1*x*y+.2*sqrt(abs(x))
    dLat+=(20*sin(6*x*PI)+20*sin(2*x*PI))*2/3
    dLat+=(20*sin(y*PI)+40*sin(y/3*PI))*2/3
    dLat+=(160*sin(y/12*PI)+320*sin(y*PI/30))*2/3
    var dLon=300+x+2*y+.1*x*x+.1*x*y+.1*sqrt(abs(x))
    dLon+=(20*sin(6*x*PI)+20*sin(2*x*PI))*2/3
    dLon+=(20*sin(x*PI)+40*sin(x/3*PI))*2/3
    dLon+=(150*sin(x/12*PI)+300*sin(x/30*PI))*2/3
    val rad=lat/180*PI; val magic=1-.006693421622965943*sin(rad).pow(2); val sqrtM=sqrt(magic)
    dLat=dLat*180/(6378245*(1-.006693421622965943)/(magic*sqrtM)*PI)
    dLon=dLon*180/(6378245/sqrtM*cos(rad)*PI)
    return lat+dLat to lon+dLon
}

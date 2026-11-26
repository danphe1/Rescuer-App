package org.nepalscouts.rescuer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.nepalscouts.rescuer.data.RescueDatabase

class MapActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        val banner = TextView(this).apply {
            text = "Operational Map · OpenStreetMap"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(5, 93, 55))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(banner, LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        lifecycleScope.launch {
            val points = RescueDatabase.get(this@MapActivity).locationDao().recent(1000).reversed()
            val route = JSONArray()
            points.forEach { p -> route.put(JSONArray().put(p.latitude).put(p.longitude)) }
            val centerLat = points.lastOrNull()?.latitude ?: 27.7172
            val centerLng = points.lastOrNull()?.longitude ?: 85.3240
            web.loadDataWithBaseURL(
                "https://www.openstreetmap.org/",
                html(route.toString(), centerLat, centerLng),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private fun html(route: String, lat: Double, lng: Double) = """
        <!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>
        <link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
        <script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>
        <style>html,body,#map{height:100%;margin:0} .leaflet-control-attribution{font-size:10px}</style></head>
        <body><div id='map'></div><script>
        const map=L.map('map').setView([$lat,$lng],14);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);
        const route=$route;
        if(route.length){const line=L.polyline(route,{weight:5}).addTo(map); map.fitBounds(line.getBounds(),{padding:[24,24]});
          const last=route[route.length-1]; L.circleMarker(last,{radius:8,weight:3,fillOpacity:1}).addTo(map).bindPopup('Latest locally captured position');}
        </script></body></html>
    """.trimIndent()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

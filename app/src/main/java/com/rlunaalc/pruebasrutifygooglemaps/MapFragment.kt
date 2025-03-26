package com.rlunaalc.pruebasrutifygooglemaps

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.button.MaterialButton
import com.rlunaalc.pruebasrutifygooglemaps.databinding.FragmentMapBinding
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONObject

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    var points = mutableListOf<LatLng>()
    private lateinit var polyline: Polyline
    private lateinit var binding: FragmentMapBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMapBinding.inflate(inflater, container, false)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.drawRouteButton.setOnClickListener {
            if (points.size >= 2) {
                drawRoute(points)
            } else {
                Log.e("MapFragment", "No hay suficientes puntos para trazar la ruta.")
            }
        }

        return binding.root
    }

    fun drawPolyline(routePoints: List<LatLng>) {
        if (routePoints.isEmpty()) {
            Log.d("MapFragment", "No hay puntos para dibujar la línea.")
            return
        }

        val polylineOptions = PolylineOptions().color(Color.BLUE).width(5f).addAll(routePoints)

        polyline = mMap.addPolyline(polylineOptions)
        Log.d("MapFragment", "Línea dibujada con ${routePoints.size} puntos.")

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.last(), 15f))
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE

        mMap.setOnMapClickListener { latLng ->
            points.add(latLng)
            Log.d("MapFragment", "Punto agregado: $latLng")
            mMap.addMarker(MarkerOptions().position(latLng).title("Punto"))
        }
    }

    private fun drawRoute(points: List<LatLng>) {
        val start = points.first()
        val end = points.last()
        Log.d("MapFragment", "Solicitando ruta de $start a $end")

        val apiKey = "AIzaSyCBFA9WsXROSbgsZF2KWa5iBdAX365vu44"
        val waypoints = points.drop(1).dropLast(1).joinToString("|") { "${it.latitude},${it.longitude}" }
        val waypointsParam = if (waypoints.isNotEmpty()) "&waypoints=$waypoints" else ""
        val directionsUrl = "https://maps.googleapis.com/maps/api/directions/json?origin=${start.latitude},${start.longitude}&destination=${end.latitude},${end.longitude}$waypointsParam&alternatives=false&steps=true&key=$apiKey"

        val queue = Volley.newRequestQueue(context)
        val stringRequest = StringRequest(Request.Method.GET, directionsUrl,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val routes = jsonResponse.getJSONArray("routes")
                    if (routes.length() == 0) {
                        Log.e("MapFragment", "No se encontraron rutas.")
                        return@StringRequest
                    }

                    val legs = routes.getJSONObject(0).getJSONArray("legs")
                    val routePoints = mutableListOf<LatLng>()
                    for (i in 0 until legs.length()) {
                        val steps = legs.getJSONObject(i).getJSONArray("steps")
                        for (j in 0 until steps.length()) {
                            val polyline = steps.getJSONObject(j).getJSONObject("polyline")
                            val decodedPoints = decodePoly(polyline.getString("points"))
                            routePoints.addAll(decodedPoints)
                        }
                    }

                    drawPolyline(routePoints)
                } catch (e: Exception) {
                    Log.e("MapFragment", "Error al procesar la respuesta de la API.", e)
                }
            },
            { error ->
                Log.e("MapFragment", "Error en la solicitud: $error")
            })

        queue.add(stringRequest)
    }

    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if (result and 1 != 0) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if (result and 1 != 0) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
            lng += dlng

            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }
}
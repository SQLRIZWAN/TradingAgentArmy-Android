package com.rizwan.tradingagentarmy.ui.market

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private data class MarketRow(val symbol: String, val name: String, val price: Double, val change: Double)
private suspend fun getText(url: String): String = withContext(Dispatchers.IO) { URL(url).openConnection().apply { connectTimeout=9000; readTimeout=12000 }.getInputStream().bufferedReader().use { it.readText() } }

@Composable
fun MarketScreen(onChart: (String) -> Unit) {
    var group by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<MarketRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun load(which: Int) {
        loading = true; problem = ""
        try {
            rows = when(which) {
                0 -> JSONArray(getText("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false&price_change_percentage=24h")).let { arr -> (0 until arr.length()).map { val o=arr.getJSONObject(it); MarketRow("${o.getString("symbol").uppercase()}/USDT",o.getString("name"),o.optDouble("current_price"),o.optDouble("price_change_percentage_24h")) } }
                1 -> {
                    val currencies=listOf("USD","EUR","GBP","JPY","CHF","CAD","AUD","NZD","CNY","HKD","SGD","SEK","NOK","MXN","ZAR","TRY")
                    val rates=JSONObject(getText("https://api.frankfurter.app/latest?from=USD&to=${currencies.filter { it!="USD" }.joinToString(",")}" )).getJSONObject("rates")
                    currencies.filter { it!="USD" }.map { c -> MarketRow("${c}USD","$c / USD",1.0/rates.getDouble(c),0.0) }
                }
                else -> listOf("XAU" to "Gold", "XAG" to "Silver", "HG" to "Copper", "XPT" to "Platinum", "XPD" to "Palladium").mapNotNull { (code,name) -> runCatching { val p=JSONObject(getText("https://api.gold-api.com/price/$code")).optDouble("price"); if(p>0) MarketRow("${code}USD",name,p,0.0) else null }.getOrNull() }
            }
        } catch (e: Exception) { problem = e.message ?: "Market data is temporarily unavailable" }
        loading=false
    }
    LaunchedEffect(group) { load(group) }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        TabRow(selectedTabIndex=group) { listOf("Crypto","Forex","Metals").forEachIndexed { i,s -> Tab(group==i,{group=i},text={Text(s)}) } }
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(when(group){0->"Top 100 by market cap";1->"Major currency pairs";else->"Precious metals & copper"},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            IconButton(onClick={ scope.launch { load(group) } }) { Icon(Icons.Filled.Refresh,"Refresh") }
        }
        if (loading && rows.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(problem.isNotBlank()) Text(problem,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)) {
            items(rows) { item -> ElevatedCard(Modifier.fillMaxWidth().clickable { onChart(item.symbol) }) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.name,fontWeight=FontWeight.SemiBold); Text(item.symbol,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Column(horizontalAlignment=Alignment.End) { Text(if(item.price>=100) "${"%,.2f".format(item.price)}" else "%.5f".format(item.price)); Text(if(item.change==0.0) "Market quote" else "%+.2f%%".format(item.change),color=if(item.change>=0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium) }
                    IconButton(onClick={onChart(item.symbol)}) { Icon(Icons.Filled.CandlestickChart,"Open chart") }
                }
            } }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChartScreen(symbol: String, onBack: () -> Unit = {}) {
    val context= LocalContext.current
    val pair=remember(symbol) { when { symbol.endsWith("/USDT") -> "BINANCE:${symbol.replace("/","")}"; symbol.endsWith("USD") && symbol.startsWith("XAU") -> "OANDA:XAUUSD"; symbol.startsWith("XAG") -> "OANDA:XAGUSD"; symbol.startsWith("HG") -> "COMEX:HG1!"; symbol.startsWith("XPT") -> "OANDA:XPTUSD"; symbol.startsWith("XPD") -> "OANDA:XPDUSD"; else -> "FX:${symbol.replace("/","")}" } }
    Scaffold(topBar={ TopAppBar(title={Text("$symbol chart")}, navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}}) }) { padding -> AndroidView(modifier=Modifier.fillMaxSize().padding(padding), factory={ WebView(context).apply { settings.javaScriptEnabled=true; settings.domStorageEnabled=true; loadDataWithBaseURL("https://www.tradingview.com", """
      <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body,#chart{height:100%;margin:0}</style></head><body><div id="chart"></div><script src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>{"autosize":true,"symbol":"$pair","interval":"15","timezone":"Etc/UTC","theme":"dark","style":"1","locale":"en","allow_symbol_change":true,"withdateranges":true,"hide_side_toolbar":false,"details":true,"hotlist":true,"calendar":false,"studies":["Volume@tv-basicstudies","RSI@tv-basicstudies"]}</script></body></html>
    """.trimIndent(),"text/html","UTF-8",null) } }) }
}

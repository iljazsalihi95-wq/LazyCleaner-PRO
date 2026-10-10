package com.lazycleaner.pro

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.*
import java.security.MessageDigest

class MainActivity : Activity() {
 private val pickerCode = 101
 private val exportCode = 102
 private val backupPickCode = 103
 private val backupSaveCode = 104
 private var backupSource: Uri? = null
 private val cleanupCandidates=mutableListOf<Pair<Uri,String>>()
 private var lastReport = ""
 private var latestScanFolder: Uri? = null
 private lateinit var result: TextView
 private lateinit var container: LinearLayout
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = ScrollView(this)
  container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,48,28,28); setBackgroundColor(Color.rgb(3,16,37)) }
  root.addView(container)
  title("♻ LazyCleaner PRO")
  val logo=ImageView(this).apply { setImageResource(R.drawable.ic_lazycleaner); layoutParams=LinearLayout.LayoutParams(180,180) }
  container.addView(logo)
  text("Smart Phone Manager • Skanim lokal, pa fshirje automatike")
  val stats=StatFs(Environment.getDataDirectory().absolutePath)
  val totalGb=stats.totalBytes.toDouble()/1073741824.0
  val freeGb=stats.availableBytes.toDouble()/1073741824.0
  text("📊 Hapësira e pajisjes: %.1f GB të lira nga %.1f GB".format(freeGb,totalGb))
  val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
   max=1000
   progress=if(totalGb>0) (((totalGb-freeGb)/totalGb)*1000).toInt().coerceIn(0,1000) else 0
   progressTintList=android.content.res.ColorStateList.valueOf(Color.CYAN)
  }
  container.addView(bar)
  text("🧹 SMART SCAN")

  button("🔎 Zgjidh dosjen për skanim") {
   val i=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
   startActivityForResult(i,pickerCode)
  }
  button("💾 Ruaj raportin (.txt)") {
   if(lastReport.isBlank()) Toast.makeText(this,"Së pari skano një dosje",Toast.LENGTH_SHORT).show()
   else startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type="text/plain"
    putExtra(Intent.EXTRA_TITLE,"LazyCleaner_Report.txt")
   },exportCode)
  }
  button("📂 Hap dosjen e fundit të skanuar") {
   val folder=latestScanFolder
   if(folder==null) Toast.makeText(this,"Së pari zgjidh një dosje",Toast.LENGTH_SHORT).show()
   else try {
    startActivity(Intent(Intent.ACTION_VIEW).apply {
     setDataAndType(folder,DocumentsContract.Document.MIME_TYPE_DIR)
     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
   } catch (_:Exception) { Toast.makeText(this,"Hape dosjen nga Files",Toast.LENGTH_LONG).show() }
  }
  button("🧾 Kandidatët për pastrim (pa fshirje)") {
   result.text=if(cleanupCandidates.isEmpty()) "Së pari skano një dosje. Nuk ka kandidatë të identifikuar."
    else "Duplikate identike (skedarët e projekteve janë përjashtuar) – shqyrto para çdo veprimi:\n"+cleanupCandidates.take(80).joinToString("\n") { "• "+it.second }+"\n\nAsnjë skedar nuk është fshirë."
  }
  button("🛡️ Smart Backup – kopjo një skedar") {
   val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type="*/*"
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }
   startActivityForResult(intent,backupPickCode)
  }
  button("🕘 Historiku i skanimeve") {
   val history=getSharedPreferences("scan_history",MODE_PRIVATE).getString("recent","Nuk ka skanime të përfunduara.")
   result.text=history
  }
  result=TextView(this).apply { setTextColor(Color.WHITE); textSize=15f; setPadding(0,20,0,12) }
  container.addView(result)
  text("🛡️ HTML Project Guardian: skedarët index.html dhe versionet e ndryshme nuk fshihen automatikisht.")
  text("📸 Duplikatet identike identifikohen me SHA-256. Fotografitë e ngjashme kërkojnë analizë tjetër.")
  text("♻️ Smart Backup kopjon një skedar të zgjedhur. Email Cleaner dhe AI janë ende në zhvillim.")
  setContentView(root)
 }
 private fun title(s:String){ val v=TextView(this).apply { text=s; textSize=29f; setTextColor(Color.CYAN); setTypeface(null,Typeface.BOLD); setPadding(0,0,0,20) }; container.addView(v) }
 private fun text(s:String){ val v=TextView(this).apply { text=s; textSize=16f; setTextColor(Color.WHITE); setPadding(0,12,0,12) }; container.addView(v) }
 private fun button(s:String, click:()->Unit){ val b=Button(this).apply { text=s; setOnClickListener { click() } }; container.addView(b) }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(resultCode!=RESULT_OK) return
  if(requestCode==backupPickCode) {
   backupSource=data?.data ?: return
   val name=try {
    contentResolver.query(backupSource!!,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
     if(it.moveToFirst()) it.getString(0) else "backup.bin"
    } ?: "backup.bin"
   } catch (_:Exception) { "backup.bin" }
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type="application/octet-stream"
    putExtra(Intent.EXTRA_TITLE,"Backup_"+name)
   },backupSaveCode)
   return
  }
  if(requestCode==backupSaveCode) {
   val source=backupSource
   val target=data?.data
   if(source!=null && target!=null) {
    Thread {
     try {
      val input=contentResolver.openInputStream(source) ?: throw java.io.IOException("Cannot open source")
      val output=contentResolver.openOutputStream(target) ?: throw java.io.IOException("Cannot open destination")
      input.use { i -> output.use { o -> i.copyTo(o) } }
      runOnUiThread { Toast.makeText(this,"Kopja rezervë u krijua",Toast.LENGTH_LONG).show() }
     } catch (_:Exception) {
      runOnUiThread { Toast.makeText(this,"Backup dështoi. Kontrollo hapësirën dhe lejet.",Toast.LENGTH_LONG).show() }
     }
    }.start()
   }
   backupSource=null
   return
  }
  if(requestCode==exportCode) {
   try {
    val target=data?.data ?: return
    contentResolver.openOutputStream(target)?.use { it.write(lastReport.toByteArray(Charsets.UTF_8)) }
    Toast.makeText(this,"Raporti u ruajt",Toast.LENGTH_SHORT).show()
   } catch(e:Exception) { Toast.makeText(this,"Gabim gjatë ruajtjes",Toast.LENGTH_LONG).show() }
   return
  }
  if(requestCode!=pickerCode) return
  val uri=data?.data ?: return
  try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}
  latestScanFolder=uri
  result.text="Duke skanuar..."
  Thread {
   val hashes=mutableMapOf<String,MutableList<Pair<Uri,String>>>()
   val fileSizes=mutableListOf<Pair<String,Long>>()
   var scannedBytes=0L
   var total=0; var html=0; var screenshots=0; var failures=0
   val htmlFiles=mutableListOf<String>()
   fun scan(folder:Uri,depth:Int){
    if(depth>20 || total>=20000) return
    val children=DocumentsContract.buildChildDocumentsUriUsingTree(folder,if(depth==0) DocumentsContract.getTreeDocumentId(folder) else DocumentsContract.getDocumentId(folder))
    val projection=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE)
    contentResolver.query(children,projection,null,null,null)?.use { cursor ->
     while(cursor.moveToNext() && total<20000){
      val id=cursor.getString(0); val name=cursor.getString(1) ?: ""; val mime=cursor.getString(2)
      val child=DocumentsContract.buildDocumentUriUsingTree(uri,id)
      val size=if(cursor.isNull(3)) 0L else cursor.getLong(3)
      if(mime==DocumentsContract.Document.MIME_TYPE_DIR){scan(child,depth+1);continue}
      total++; scannedBytes+=size; if(fileSizes.size<20000) fileSizes.add(name to size); if(name.endsWith(".html",true)) { html++; if(htmlFiles.size<100) htmlFiles.add(name) }
      if(name.contains("screenshot",true)) screenshots++
      try {
       val md=MessageDigest.getInstance("SHA-256")
       contentResolver.openInputStream(child)?.use { input ->
        val buf=ByteArray(32768)
        while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}
        val digest=md.digest().joinToString(""){"%02x".format(it)}
        hashes.getOrPut(digest){mutableListOf()}.add(child to name)
       }
      } catch (_:Exception){failures++}
     }
    }
   }
   try {scan(uri,0)} catch (_:Exception){failures++}
   val duplicates=hashes.values.filter{it.size>1}
   val candidates=duplicates.flatMap { it.drop(1) }.filterNot { (_, name) -> name.endsWith(".html",true) || name.endsWith(".htm",true) || name.endsWith(".js",true) || name.endsWith(".gs",true) || name.endsWith(".json",true) }
   val largest=fileSizes.sortedByDescending { it.second }.take(15).joinToString("\n") { "${it.first}: ${it.second/1048576} MB" }
   val message="Skedarë: $total\nHTML: $html\nScreenshot: $screenshots\nGrupe identike: ${duplicates.size}\nMadhësia e skanuar: ${scannedBytes/1048576} MB\nGabime leximi: $failures\n\n"+
    duplicates.take(25).joinToString("\n\n"){ "Duplikate:\n"+it.joinToString("\n") { item -> item.second } }+
    "\n\n15 skedarët më të mëdhenj:\n"+largest+"\n\nHTML të gjetura:\n"+htmlFiles.take(100).joinToString("\n")+"\n\nAsgjë nuk është fshirë."
   runOnUiThread {
    cleanupCandidates.clear()
    cleanupCandidates.addAll(candidates)
    lastReport=message
    result.text=message
    val prefs=getSharedPreferences("scan_history",MODE_PRIVATE)
    val now=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
    val entry="$now — $total skedarë, $html HTML, ${duplicates.size} grupe duplikatesh"
    val previous=prefs.getString("recent","") ?: ""
    prefs.edit().putString("recent",(entry+"\n"+previous).lines().take(20).joinToString("\n")).apply()
   }
  }.start()
 }
}

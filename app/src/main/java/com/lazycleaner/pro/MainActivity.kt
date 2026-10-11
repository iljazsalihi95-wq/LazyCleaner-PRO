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
 private val mediaPermissionCode = 201
 private val deleteRequestCode = 202
 private val selectedMedia=linkedSetOf<Uri>()
 private val mediaCandidates=mutableListOf<Pair<Uri,String>>()
 private lateinit var candidatesList: LinearLayout
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
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun label(s:String,size:Float=15f,color:Int=Color.WHITE,bold:Boolean=false):TextView =
  TextView(this).apply { text=s; textSize=size; setTextColor(color); if(bold)setTypeface(null,Typeface.BOLD); gravity=android.view.Gravity.CENTER_VERTICAL }
 private fun card():LinearLayout = LinearLayout(this).apply {
  orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14))
  background=android.graphics.drawable.GradientDrawable().apply {
   setColor(Color.rgb(15,36,67)); cornerRadius=dp(18).toFloat(); setStroke(dp(1),Color.rgb(34,67,103))
  }
  layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
 }
 private fun action(parent:LinearLayout,icon:String,title:String,detail:String,click:()->Unit){
  val row=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL
   setPadding(dp(12),dp(12),dp(12),dp(12))
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(22,49,83)); cornerRadius=dp(14).toFloat()
   }
   layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) }
   setOnClickListener { click() }
  }
  row.addView(label(icon,24f),LinearLayout.LayoutParams(dp(44),dp(46)))
  val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  content.addView(label(title,15f,Color.WHITE,true))
  content.addView(label(detail,12f,Color.rgb(166,193,219)))
  row.addView(content,LinearLayout.LayoutParams(0,-2,1f))
  row.addView(label("›",26f,Color.rgb(52,220,232)))
  parent.addView(row)
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(4,16,34)
  window.navigationBarColor=Color.rgb(4,16,34)
  val root=ScrollView(this).apply { isFillViewport=true; clipToPadding=false }
  container=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(18),dp(16),dp(42))
   background=android.graphics.drawable.GradientDrawable(
    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.rgb(8,32,64),Color.rgb(3,12,28))
   )
  }
  root.addView(container)
  val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
  header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_lazycleaner) },LinearLayout.LayoutParams(dp(66),dp(66)))
  val names=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  names.addView(label("LazyCleaner PRO",24f,Color.WHITE,true))
  names.addView(label("Smart Phone Manager  •  Privat & Lokal",12f,Color.rgb(113,219,232)))
  header.addView(names,LinearLayout.LayoutParams(0,-2,1f))
  container.addView(header,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(20) })
  val stats=StatFs(Environment.getDataDirectory().absolutePath)
  val totalGb=stats.totalBytes.toDouble()/1073741824.0
  val freeGb=stats.availableBytes.toDouble()/1073741824.0
  val storage=card()
  storage.addView(label("HAPËSIRA E TELEFONIT",12f,Color.rgb(127,195,221),true))
  storage.addView(label("%.1f GB të lira".format(freeGb),27f,Color.WHITE,true))
  storage.addView(label("Nga %.1f GB gjithsej".format(totalGb),13f,Color.rgb(172,197,218)))
  val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
   max=1000; progress=if(totalGb>0)(((totalGb-freeGb)/totalGb)*1000).toInt().coerceIn(0,1000) else 0
   progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(45,216,220))
   progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(38,63,89))
  }
  storage.addView(bar,LinearLayout.LayoutParams(-1,dp(14)).apply { topMargin=dp(12) })
  container.addView(storage)
  val tools=card()
  tools.addView(label("MJETET E PASTRIMIT",13f,Color.rgb(65,224,226),true),
   LinearLayout.LayoutParams(-1,dp(35)))
  action(tools,"📱","SKANO TELEFONIN","Skanon median pa zgjedhur dosje"){ startPhoneScan() }
  action(tools,"🔍","Skano një dosje","Vetëm nën-dosje të lejuara nga Android"){
   val i=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
   startActivityForResult(i,pickerCode)
  }
  action(tools,"🗑️","Zgjidh dhe fshi duplikatet","Shiko listën, selekto dhe konfirmo fshirjen"){ showCandidates() }
  action(tools,"📸","Screenshot-et","Shiko fotografitë e ekranit për përzgjedhje"){ showScreenshots() }
  action(tools,"📦","Aplikacionet e instaluara","Zgjidh aplikacionin për çinstalim"){ showInstalledApps() }
  action(tools,"🧾","Raporti i kandidatëve","Shiko listën pa fshirje"){
   result.text=if(cleanupCandidates.isEmpty()) "Shtyp SKANO TELEFONIN për të analizuar median."
    else "Duplikate identike (skedarët e projekteve janë përjashtuar):\n"+cleanupCandidates.take(80).joinToString("\n") { "• "+it.second }+"\n\nAsnjë skedar nuk është fshirë."
  }
  action(tools,"🛡️","Smart Backup","Kopjo një skedar në vend të sigurt"){
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE);type="*/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   },backupPickCode)
  }
  action(tools,"📄","Eksporto raportin","Ruaje analizën në format TXT"){
   if(lastReport.isBlank()) Toast.makeText(this,"Së pari kryej një skanim",Toast.LENGTH_SHORT).show()
   else startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"LazyCleaner_Report.txt")
   },exportCode)
  }
  action(tools,"📂","Dosja e fundit","Hap dosjen e skanuar"){
   val folder=latestScanFolder
   if(folder==null) Toast.makeText(this,"Së pari zgjidh një dosje",Toast.LENGTH_SHORT).show()
   else try { startActivity(Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(folder,DocumentsContract.Document.MIME_TYPE_DIR);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }) } catch (_:Exception) { Toast.makeText(this,"Hape dosjen nga Files",Toast.LENGTH_LONG).show() }
  }
  action(tools,"🕘","Historiku","Shiko skanimet e mëparshme"){
   result.text=getSharedPreferences("scan_history",MODE_PRIVATE).getString("recent","Nuk ka skanime të përfunduara.")
  }
  container.addView(tools)
  val output=card()
  output.addView(label("REZULTATET E ANALIZËS",13f,Color.rgb(65,224,226),true))
  result=label("Shtyp SKANO TELEFONIN. Nuk kërkohet zgjedhja e dosjes kryesore.",14f,Color.rgb(207,225,238))
  result.setPadding(0,dp(10),0,dp(8))
  output.addView(result)
  container.addView(output)
  candidatesList=card()
  container.addView(candidatesList)
  container.addView(label("🔒 Nuk fshihen skedarë automatikisht. Projektet HTML mbrohen.",12f,Color.rgb(147,177,202)))
  setContentView(root)
 }
 private fun protectedFile(name:String):Boolean {
  val n=name.lowercase(java.util.Locale.ROOT)
  val extensions=listOf(".pdf",".doc",".docx",".xls",".xlsx",".ppt",".pptx",".txt",".rtf",".odt",".ods",".html",".htm",".js",".json",".xml",".gs",".kt",".java",".zip",".7z",".rar",".db",".sqlite",".key",".pem",".p12",".keystore")
  val sensitive=listOf("backup","kopje_rezerve","passport","pasaport","ausweis","rechnung","invoice","quittung","vertrag","contract","bank","steuer","tax","mitglied","antare","hudbe","hadith","projekt","project")
  return extensions.any { n.endsWith(it) } || sensitive.any { n.contains(it) }
 }
 private fun showSelection(items:List<Pair<Uri,String>>,heading:String){
  selectedMedia.clear();candidatesList.removeAllViews()
  candidatesList.addView(label(heading,16f,Color.rgb(61,222,226),true))
  if(items.isEmpty()){ candidatesList.addView(label("Nuk ka skedarë të listuar. Skano telefonin fillimisht.",13f));return }
  candidatesList.addView(label("Vetëm skedarët e zgjedhur nga ti mund të kërkohen për fshirje. Dokumentet e ndjeshme janë të bllokuara.",12f,Color.rgb(166,193,219)))
  for((uri,name) in items.take(300)){
   val protected=protectedFile(name)
   val check=CheckBox(this).apply {
    isEnabled=!protected
    text=(if(protected)"🔒 RUAJ: " else "Për kontroll: ")+name; textSize=13f; setTextColor(Color.WHITE);buttonTintList=android.content.res.ColorStateList.valueOf(Color.CYAN)
    setOnCheckedChangeListener { _,checked->if(checked)selectedMedia.add(uri) else selectedMedia.remove(uri) }
   }
   candidatesList.addView(check)
  }
  action(candidatesList,"🗑️","FSHI TË ZGJEDHURAT","Kërkohet miratimi i Android-it"){confirmDelete()}
 }
 private fun showCandidates(){showSelection(mediaCandidates,"Duplikatet e medias")}
 private fun showScreenshots(){
  val screenshots=mutableListOf<Pair<Uri,String>>()
  try{
   val base=android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
   val projection=arrayOf(android.provider.MediaStore.MediaColumns._ID,android.provider.MediaStore.MediaColumns.DISPLAY_NAME,android.provider.MediaStore.Images.Media.RELATIVE_PATH)
   contentResolver.query(base,projection,null,null,null)?.use{cursor->
    while(cursor.moveToNext()&&screenshots.size<300){
     val name=cursor.getString(1)?:""
     val path=cursor.getString(2)?:""
     if(name.contains("screenshot",true)||path.contains("screenshot",true))
      screenshots.add(android.content.ContentUris.withAppendedId(base,cursor.getLong(0)) to name)
    }
   }
  }catch(_:Exception){}
  showSelection(screenshots,"Screenshot-et (zgjidh për fshirje)")
 }
 private fun confirmDelete(){
  val uris=selectedMedia.toList().filter { uri -> mediaCandidates.none { it.first==uri && protectedFile(it.second) } }
  if(uris.isEmpty()){Toast.makeText(this,"Zgjidh të paktën një skedar",Toast.LENGTH_SHORT).show();return}
  android.app.AlertDialog.Builder(this).setTitle("Konfirmo fshirjen")
   .setMessage("Të kërkohet fshirja e ${uris.size} skedarëve të zgjedhur. Android do të kërkojë miratimin përfundimtar.")
   .setNegativeButton("Anulo",null).setPositiveButton("Vazhdo"){_,_->
    try{
     if(android.os.Build.VERSION.SDK_INT>=30){
      val pending=android.provider.MediaStore.createDeleteRequest(contentResolver,uris)
      startIntentSenderForResult(pending.intentSender,deleteRequestCode,null,0,0,0)
     }else Toast.makeText(this,"Fshirja në grup kërkon Android 11+",Toast.LENGTH_LONG).show()
    }catch(e:Exception){Toast.makeText(this,"Fshirja nuk u nis: ${e.message}",Toast.LENGTH_LONG).show()}
   }.show()
 }
 private fun showInstalledApps(){
  val usageManager=getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
  val end=System.currentTimeMillis()
  val usage=usageManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,end-30L*86400000,end)
  val lastUsed=usage.orEmpty().groupBy{it.packageName}.mapValues{entry->entry.value.maxOfOrNull{it.lastTimeUsed}?:0L}
  val hasUsageAccess=!usage.isNullOrEmpty()

  candidatesList.removeAllViews()
  candidatesList.addView(label("Aplikacionet – çinstalim me miratimin tënd",16f,Color.CYAN,true))
  val apps=packageManager.getInstalledApplications(0)
   .filter { it.packageName!=packageName && (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0 }
   .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
  if(!hasUsageAccess) action(candidatesList,"🔐","Lejo statistikat e përdorimit","Për të identifikuar aplikacionet e papërdorura"){startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))}
  for(app in apps.take(150)){
   val appName=packageManager.getApplicationLabel(app).toString()
   val last=lastUsed[app.packageName]?:0L
   val detail=if(!hasUsageAccess)"Çinstalo përmes Android-it" else if(last==0L)"Nuk figuron i përdorur në 30 ditë" else "Përdorur "+((end-last)/86400000)+" ditë më parë"
   action(candidatesList,"📦",appName,detail){
    android.app.AlertDialog.Builder(this).setTitle("Çinstalo $appName?")
     .setMessage("Android do të kërkojë konfirmimin tënd.")
     .setNegativeButton("Anulo",null).setPositiveButton("Vazhdo"){_,_->
      startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.packageName)))
     }.show()
   }
  }
  if(apps.isEmpty()) candidatesList.addView(label("Nuk u gjetën aplikacione të dukshme.",13f))
 }
 private fun startPhoneScan(){
  val permissions=if(android.os.Build.VERSION.SDK_INT>=33)
   arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES,android.Manifest.permission.READ_MEDIA_VIDEO,android.Manifest.permission.READ_MEDIA_AUDIO)
  else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
  val missing=permissions.filter { checkSelfPermission(it)!=android.content.pm.PackageManager.PERMISSION_GRANTED }
  if(missing.isNotEmpty()) requestPermissions(missing.toTypedArray(),mediaPermissionCode)
  else scanPhoneMedia()
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==mediaPermissionCode){
   if(grantResults.any { it==android.content.pm.PackageManager.PERMISSION_GRANTED }) scanPhoneMedia()
   else result.text="Qasja nuk u lejua. Lejo fotografitë, videot ose audion te Settings → Apps → LazyCleaner PRO → Permissions."
  }
 }
 private fun scanPhoneMedia(){
  result.text="Duke analizuar median e telefonit…"
  Thread {
   var total=0;var bytes=0L;var errors=0
   val hashes=mutableMapOf<String,MutableList<Pair<Uri,String>>>()
   val collections=listOf(
    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
   val projection=arrayOf(android.provider.MediaStore.MediaColumns._ID,
    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
    android.provider.MediaStore.MediaColumns.SIZE)
   for(base in collections){
    try {
     contentResolver.query(base,projection,null,null,null)?.use { cursor ->
      val idColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
      val nameColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
      val sizeColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
      while(cursor.moveToNext() && total<20000){
       val id=cursor.getLong(idColumn)
       val name=cursor.getString(nameColumn) ?: "media"
       val size=if(cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn)
       val file=android.content.ContentUris.withAppendedId(base,id)
       total++;bytes+=size
       try {
        val md=MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(file)?.use { input ->
         val buf=ByteArray(32768)
         while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}
         val digest=md.digest().joinToString("") { "%02x".format(it) }
         hashes.getOrPut(digest){mutableListOf()}.add(file to name)
        }
       }catch (_:Exception){errors++}
      }
     }
    }catch (_:Exception){errors++}
   }
   val duplicates=hashes.values.filter { it.size>1 }
   val candidates=duplicates.flatMap { it.drop(1) }.filterNot { protectedFile(it.second) }
   val message="Skanimi i medias përfundoi.\nSkedarë: $total\\nMadhësia: ${bytes/1048576} MB\\nGrupe duplikatesh: ${duplicates.size}\nGabime leximi: $errors\\n\\n"+
    duplicates.take(25).joinToString("\\n\\n"){group->group.joinToString("\\n"){it.second}}+
    "\\n\\nAsgjë nuk është fshirë. Dokumentet dhe dosjet e mbrojtura nga Android kërkojnë zgjedhje të veçantë."
   runOnUiThread {
    cleanupCandidates.clear();cleanupCandidates.addAll(candidates)
    mediaCandidates.clear();mediaCandidates.addAll(candidates)
    lastReport=message;result.text=message
    showSelection(candidates,"Duplikate të medias: ${candidates.size} skedarë të përzgjedhshëm")
    val prefs=getSharedPreferences("scan_history",MODE_PRIVATE)
    val now=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
    val prev=prefs.getString("recent","") ?: ""
    prefs.edit().putString("recent",("$now — Media: $total skedarë, ${duplicates.size} grupe\\n"+prev).lines().take(20).joinToString("\\n")).apply()
   }
  }.start()
 }
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
   val candidates=duplicates.flatMap { it.drop(1) }.filterNot { protectedFile(it.second) }
   val largest=fileSizes.sortedByDescending { it.second }.take(15).joinToString("\n") { "${it.first}: ${it.second/1048576} MB" }
   val message="Skedarë: $total\nHTML: $html\nScreenshot: $screenshots\nGrupe identike: ${duplicates.size}\nMadhësia e skanuar: ${scannedBytes/1048576} MB\nGabime leximi: $failures\n\n"+
    duplicates.take(25).joinToString("\n\n"){ "Duplikate:\n"+it.joinToString("\n") { item -> item.second } }+
    "\n\n15 skedarët më të mëdhenj:\n"+largest+"\n\nHTML të gjetura:\n"+htmlFiles.take(100).joinToString("\n")+"\n\nAsgjë nuk është fshirë."
   runOnUiThread {
    cleanupCandidates.clear()
    cleanupCandidates.addAll(candidates)
    mediaCandidates.clear()
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

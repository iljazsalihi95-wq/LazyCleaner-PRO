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
 private val thumbnailExecutor=java.util.concurrent.Executors.newFixedThreadPool(2)
 private val mediaPermissionCode = 201
 private val deleteRequestCode = 202
 private val uninstallRequestCode = 203
 private val compressPickCode = 204
 private val uninstallQueue=mutableListOf<String>()
 private var pendingUninstall:String?=null
 private val pendingTrash=mutableListOf<Uri>()
 private val selectedMedia=linkedSetOf<Uri>()
 private val mediaDates=mutableMapOf<Uri,Long>()
 private val verifiedDuplicates=linkedSetOf<Uri>()
 private val mediaCandidates=mutableListOf<Pair<Uri,String>>()
 private lateinit var candidatesList: LinearLayout
 private lateinit var liveRing: ProgressBar
 private var ringUpdater: ((Float)->Unit)?=null
 private lateinit var liveStatus: TextView
 private lateinit var liveMetric: TextView
 private var scanRunning=false
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
 private val phoneLanguage:String get()=resources.configuration.locales.get(0).language.lowercase(java.util.Locale.ROOT)
 private val translations=mapOf(
   "GATI PËR SKANIM" to listOf("READY TO SCAN","BEREIT ZUM SCANNEN","PRONTO PER LA SCANSIONE"),
   "0 skedarë të analizuar" to listOf("0 files analyzed","0 Dateien analysiert","0 file analizzati"),
   "SKANO LIVE" to listOf("SCAN LIVE","LIVE SCANNEN","SCANSIONE LIVE"),
   "Analizo median dhe gjej kopjet e tepërta" to listOf("Analyze media and find duplicate copies","Medien analysieren und Duplikate finden","Analizza i media e trova i duplicati"),
   "HAPËSIRA E TELEFONIT" to listOf("PHONE STORAGE","TELEFONSPEICHER","MEMORIA TELEFONO"),
   "MJETET E PASTRIMIT" to listOf("CLEANUP TOOLS","BEREINIGUNGSWERKZEUGE","STRUMENTI DI PULIZIA"),
   "SKANO TELEFONIN" to listOf("SCAN PHONE","TELEFON SCANNEN","SCANSIONA TELEFONO"),
   "Skanon median pa zgjedhur dosje" to listOf("Scan media without selecting folders","Medien ohne Ordnerauswahl scannen","Scansiona i media senza scegliere cartelle"),
   "Skano një dosje" to listOf("Scan a folder","ORDNER SCANNEN","Scansiona una cartella"),
   "Vetëm nën-dosje të lejuara nga Android" to listOf("Only folders allowed by Android","Nur von Android erlaubte Ordner","Solo cartelle consentite da Android"),
   "Zgjidh dhe fshi duplikatet" to listOf("Review duplicates","DUPLIKATE PRÜFEN","Controlla i duplicati"),
   "Shiko listën, selekto dhe konfirmo fshirjen" to listOf("Review, select and confirm removal","Ansehen, auswählen und Löschen bestätigen","Visualizza, seleziona e conferma"),
   "Screenshot-et" to listOf("Screenshots","SCREENSHOTS","Screenshot"),
   "Shiko fotografitë e ekranit për përzgjedhje" to listOf("Review screenshots before removal","Screenshots vor dem Löschen prüfen","Controlla gli screenshot prima di eliminarli"),
   "Kompreso fotografitë" to listOf("Compress photos","FOTOS KOMPRIMIEREN","Comprimi foto"),
   "Fotografi reale, pa screenshot-e; origjinalet ruhen" to listOf("Real photos only; originals are preserved","Nur Fotos; Originale bleiben erhalten","Solo foto; originali conservati"),
   "Shiko RAM-in real dhe menaxho aplikacionet" to listOf("View actual RAM and manage apps","RAM anzeigen und Apps verwalten","Visualizza RAM e gestisci app"),
   "Aplikacionet e instaluara" to listOf("Installed apps","INSTALLIERTE APPS","App installate"),
   "Raporti i kandidatëve" to listOf("Scan report","SCANBERICHT","Rapporto scansione"),
   "Smart Backup" to listOf("Smart Backup","SMART BACKUP","Backup intelligente"),
   "Eksporto raportin" to listOf("Export report","BERICHT EXPORTIEREN","Esporta rapporto"),
   "Dosja e fundit" to listOf("Last folder","LETZTER ORDNER","Ultima cartella"),
   "Historiku" to listOf("History","VERLAUF","Cronologia"),
   "ZGJIDH TË GJITHA" to listOf("SELECT ALL","ALLE AUSWÄHLEN","SELEZIONA TUTTO"),
   "HIQ PËRZGJEDHJEN" to listOf("DESELECT ALL","AUSWAHL AUFHEBEN","DESELEZIONA TUTTO"),
   "KALO NË KOSH TË ZGJEDHURAT" to listOf("MOVE SELECTED TO TRASH","AUSWAHL IN PAPIERKORB","SPOSTA SELEZIONATI NEL CESTINO"),
   "ZGJIDH VETËM DUPLIKATET" to listOf("SELECT VERIFIED DUPLICATES","NUR BESTÄTIGTE DUPLIKATE","SELEZIONA SOLO DUPLICATI"),
   "SKANIMI PËRFUNDOI" to listOf("SCAN COMPLETE","SCAN ABGESCHLOSSEN","SCANSIONE COMPLETATA"),
   "DUKE SKANUAR..." to listOf("SCANNING...","SCAN LÄUFT...","SCANSIONE IN CORSO..."),
   "DUKE KRAHASUAR..." to listOf("COMPARING...","VERGLEICH LÄUFT...","CONFRONTO IN CORSO...")
 )
 private fun tr(source:String):String {
  val index=when(phoneLanguage){"en"->0;"de"->1;"it"->2;else->-1}
  return if(index<0)source else translations[source]?.get(index)?:source
 }
 override fun onDestroy(){ thumbnailExecutor.shutdownNow();super.onDestroy() }
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun label(s:String,size:Float=15f,color:Int=Color.WHITE,bold:Boolean=false):TextView =
  TextView(this).apply { text=tr(s); textSize=size; setTextColor(color); if(bold)setTypeface(null,Typeface.BOLD); gravity=android.view.Gravity.CENTER_VERTICAL }
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
  content.addView(label(tr(title),15f,Color.WHITE,true))
  content.addView(label(tr(detail),12f,Color.rgb(166,193,219)))
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
  val hero=card()
  hero.addView(label("LAZYCLEANER PRO • LIVE",13f,Color.rgb(65,224,226),true))
  val circle=android.widget.FrameLayout(this)
  liveRing=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
   max=1000
   progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(36,218,234))
   progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(36,65,97))
  }
  val ringView=object:android.view.View(this){
   private val paint=android.graphics.Paint(3).apply{style=android.graphics.Paint.Style.STROKE;strokeWidth=dp(16).toFloat();strokeCap=android.graphics.Paint.Cap.ROUND}
   var fraction=0f
   override fun onDraw(canvas:android.graphics.Canvas){
    super.onDraw(canvas)
    val inset=dp(20).toFloat()
    val bounds=android.graphics.RectF(inset,inset,width-inset,height-inset)
    paint.color=Color.rgb(35,68,105)
    canvas.drawArc(bounds,0f,360f,false,paint)
    paint.color=Color.rgb(44,221,237)
    canvas.drawArc(bounds,-90f,360f*fraction,false,paint)
   }
   fun updateRingProgress(value:Float){fraction=value.coerceIn(0f,1f);invalidate()}
  }
  circle.addView(ringView,android.widget.FrameLayout.LayoutParams(dp(240),dp(240),android.view.Gravity.CENTER))
  ringUpdater={ value -> ringView.updateRingProgress(value) }
  val center=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER}
  liveStatus=label("GATI PËR SKANIM",19f,Color.WHITE,true).apply{gravity=android.view.Gravity.CENTER}
  liveMetric=label("0 skedarë të analizuar",13f,Color.rgb(166,214,233)).apply{gravity=android.view.Gravity.CENTER}
  center.addView(liveStatus);center.addView(liveMetric)
  circle.addView(center,android.widget.FrameLayout.LayoutParams(dp(210),dp(120),android.view.Gravity.CENTER))
  hero.addView(circle,LinearLayout.LayoutParams(-1,dp(252)))
  action(hero,"🔍","SKANO LIVE","Analizo median dhe gjej kopjet e tepërta"){startPhoneScan()}
  container.addView(hero)
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
  action(tools,"🖼️","Kompreso fotografitë","Fotografi reale, pa screenshot-e; origjinalet ruhen"){ pickPhotosForCompression() }
  action(tools,"🚀","Process Manager • RAM","Shiko RAM-in real dhe menaxho aplikacionet"){ showProcessManager() }
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
 private fun showSelection(items:List<Pair<Uri,String>>,heading:String,autoRecommend:Boolean=false){
  selectedMedia.clear();candidatesList.removeAllViews()
  candidatesList.addView(label(heading,16f,Color.rgb(61,222,226),true))
  if(items.isEmpty()){candidatesList.addView(label("Nuk u gjetën kandidatë.",13f));return}
  candidatesList.addView(label("Kopjet identike të vjetra rekomandohen. Screenshot-et e tjera kontrolloji para fshirjes. Një kopje e çdo grupi ruhet.",12f,Color.rgb(166,193,219)))
  val checks=mutableListOf<CheckBox>()
  for((uri,name) in items.take(300)){
   val duplicate=verifiedDuplicates.contains(uri)
   val sensitive=protectedFile(name)
   val check=CheckBox(this).apply {
    isEnabled=true
    text=(if(duplicate)"✓ KOPJE E TEPËRT: " else if(sensitive)"⚠ DOKUMENT – KONTROLLO: " else "Për kontroll: ")+name
    textSize=13f;setTextColor(Color.WHITE)
    buttonTintList=android.content.res.ColorStateList.valueOf(Color.CYAN)
    isChecked=autoRecommend && duplicate
    setOnCheckedChangeListener { _,checked->if(checked)selectedMedia.add(uri) else selectedMedia.remove(uri) }
   }
   if(check.isChecked) selectedMedia.add(uri)
   val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
   val thumb=ImageView(this).apply {
    scaleType=ImageView.ScaleType.CENTER_CROP
    setImageResource(android.R.drawable.ic_menu_gallery)
    setOnClickListener {
     val full=ImageView(this@MainActivity).apply { setImageURI(uri); adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER }
     android.app.AlertDialog.Builder(this@MainActivity).setTitle(name).setView(full).setPositiveButton("Mbyll",null).show()
    }
   }
   row.addView(thumb,LinearLayout.LayoutParams(dp(72),dp(72)).apply { rightMargin=dp(10) })
   row.addView(check,LinearLayout.LayoutParams(0,-2,1f))
   checks.add(check);candidatesList.addView(row)
   if(checks.size<=60){
    thumbnailExecutor.execute {
     try{
      val bitmap=contentResolver.loadThumbnail(uri,android.util.Size(144,144),null)
      runOnUiThread { if(thumb.isAttachedToWindow)thumb.setImageBitmap(bitmap) else bitmap.recycle() }
     }catch(_:Exception){}
    }
   }
  }
  action(candidatesList,"☑","ZGJIDH TË GJITHA","Përzgjedh të gjithë kandidatët e shfaqur"){checks.forEach { it.isChecked=true }}
  action(candidatesList,"↩","HIQ PËRZGJEDHJEN","Lë gjithçka pa shënuar"){checks.forEach { it.isChecked=false }}
  action(candidatesList,"🗑️","KALO NË KOSH TË ZGJEDHURAT","Me konfirmim nga Android"){confirmDelete()}
 }
 private fun showCandidates(){showSelection(mediaCandidates,"Duplikatet e medias",true)}
 private fun showScreenshots(){
  val screenshots=mutableListOf<Pair<Uri,String>>()
  try{
   val base=android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
   val projection=arrayOf(android.provider.MediaStore.MediaColumns._ID,
    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
    android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
   contentResolver.query(base,projection,null,null,
    android.provider.MediaStore.MediaColumns.DATE_MODIFIED+" DESC")?.use{cursor->
    val idCol=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
    val nameCol=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
    val pathCol=cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
    while(cursor.moveToNext()&&screenshots.size<300){
     val name=cursor.getString(nameCol)?:""
     val path=cursor.getString(pathCol)?:""
     if(name.contains("screenshot",true)||name.contains("screen_shot",true)||path.contains("screenshot",true))
      screenshots.add(android.content.ContentUris.withAppendedId(base,cursor.getLong(idCol)) to name)
    }
   }
  }catch(e:Exception){result.text="Screenshot-et nuk u lexuan: ${e.message}"}
  showSelection(screenshots,"Screenshot-et • ${screenshots.size} për kontroll")
  if(screenshots.isNotEmpty()){
   val candidates=screenshots.count{verifiedDuplicates.contains(it.first)}
   candidatesList.addView(label("Kopje identike të verifikuara: $candidates. Fotot e tjera nuk përzgjidhen automatikisht pa miratimin tënd.",12f,Color.rgb(166,193,219)))
   action(candidatesList,"🧠","ZGJIDH VETËM DUPLIKATET","Përzgjedh vetëm kopjet e verifikuara nga skanimi"){
    val duplicates=screenshots.filter{verifiedDuplicates.contains(it.first)}
    showSelection(duplicates,"Screenshot-e identike • ${duplicates.size} kopje",true)
   }
  }
 }
 private fun confirmDelete(){
  val uris=selectedMedia.toList().distinct()
  if(uris.isEmpty()){
   Toast.makeText(this,"Zgjidh të paktën një skedar të lejuar",Toast.LENGTH_LONG).show()
   return
  }
  android.app.AlertDialog.Builder(this)
   .setTitle("Zhvendos në kosh?")
   .setMessage("Do të kërkohet miratimi i Android-it për ${uris.size} skedarë. Fillimisht zhvendosen në kosh, jo fshirje e pakthyeshme.")
   .setNegativeButton("Anulo",null)
   .setPositiveButton("Vazhdo"){_,_->
    try{
     if(android.os.Build.VERSION.SDK_INT>=30){
      val batch=uris.take(100)
      pendingTrash.clear();pendingTrash.addAll(batch)
      val pending=android.provider.MediaStore.createTrashRequest(contentResolver,batch,true)
      startIntentSenderForResult(pending.intentSender,deleteRequestCode,null,0,0,0)
      if(uris.size>batch.size) Toast.makeText(this,"Po trajtohen 100 të parat. Përsërite për të tjerat.",Toast.LENGTH_LONG).show()
     }else result.text="Ky version Android kërkon mënyrë tjetër fshirjeje."
    }catch(e:Exception){
     val detail="${e.javaClass.simpleName}: ${e.message ?: "pa hollësi"}"
     result.text="Kërkesa për koshin dështoi: $detail"
     android.app.AlertDialog.Builder(this).setTitle("Gabim gjatë pastrimit")
      .setMessage(detail+"\nNuk është fshirë asgjë.").setPositiveButton("OK",null).show()
    }
   }.show()
 }
 private fun pickPhotosForCompression(){
  android.app.AlertDialog.Builder(this).setTitle("Kompresim fotografish")
   .setMessage("Zgjidh fotografitë reale. Screenshot-et do të anashkalohen. Kopjet e kompresuara ruhen në Pictures/LazyCleaner; origjinalet nuk fshihen. Fotot JPEG ruhen me cilësi 82%.")
   .setNegativeButton("Anulo",null).setPositiveButton("Zgjidh fotografitë"){_,_->
    val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     type="image/*"
     addCategory(Intent.CATEGORY_OPENABLE)
     putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivityForResult(intent,compressPickCode)
   }.show()
 }
 private fun compressPhotos(data:Intent?){
  val uris=mutableListOf<Uri>()
  data?.clipData?.let { clip->for(i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
  if(uris.isEmpty()) data?.data?.let { uris.add(it) }
  if(uris.isEmpty()) return
  result.text="Duke kompresuar ${uris.size} fotografi..."
  Thread{
   var saved=0
   var skipped=0
   var before=0L
   var after=0L
   for(uri in uris.take(50)){
    try{
     val name=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
      if(it.moveToFirst()) it.getString(0) else "foto"
     } ?: "foto"
     if(name.contains("screenshot",true)||name.contains("screen_shot",true)){skipped++;continue}
     val sourceSize=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use{
      if(it.moveToFirst()&&!it.isNull(0))it.getLong(0) else 0L
     } ?: 0L
     if(sourceSize>35L*1024*1024){skipped++;continue}
     val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
     contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,options)}
     if(options.outWidth<=0||options.outHeight<=0){skipped++;continue}
     var sample=1
     while(options.outWidth/sample>2560||options.outHeight/sample>2560)sample*=2
     val decodeOptions=android.graphics.BitmapFactory.Options().apply{inSampleSize=sample}
     val bitmap=contentResolver.openInputStream(uri)?.use{
      android.graphics.BitmapFactory.decodeStream(it,null,decodeOptions)
     } ?: continue
     val buffer=java.io.ByteArrayOutputStream()
     bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,buffer)
     val compressed=buffer.toByteArray()
     bitmap.recycle()
     before+=sourceSize
     if(sourceSize<=0L||compressed.size>=sourceSize){skipped++;continue}
     val values=android.content.ContentValues().apply{
      put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"LazyCleaner_"+name.substringBeforeLast('.')+".jpg")
      put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/jpeg")
      put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/LazyCleaner")
     }
     val dest=contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: continue
     try{
      contentResolver.openOutputStream(dest)?.use { it.write(compressed) } ?: throw java.io.IOException("Output unavailable")
      after+=compressed.size;saved++
     }catch(e:Exception){contentResolver.delete(dest,null,null);throw e}
    }catch(_:Exception){skipped++}
   }
   runOnUiThread{
    result.text="Kompresimi përfundoi. Kopje të reja: $saved. Të anashkaluara: $skipped. Kursim i mundshëm pas kontrollit dhe heqjes së origjinaleve: ${(before-after).coerceAtLeast(0L)/1024} KB. Origjinalet janë ruajtur. Fotot e reja gjenden te Pictures/LazyCleaner."
   }
  }.start()
 }
 private fun showProcessManager(){
  val manager=getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
  val memory=android.app.ActivityManager.MemoryInfo()
  manager.getMemoryInfo(memory)
  val total=memory.totalMem
  val available=memory.availMem
  val used=(total-available).coerceAtLeast(0L)
  val percent=if(total>0) (used*100.0/total) else 0.0
  val gb=1073741824.0
  candidatesList.removeAllViews()
  candidatesList.addView(label("🚀 PROCESS MANAGER",20f,Color.CYAN,true))
  candidatesList.addView(label("Gjendja reale e memories RAM",13f,Color.rgb(170,202,226)))
  candidatesList.addView(label("%.1f%% RAM i përdorur".format(percent),26f,Color.WHITE,true))
  candidatesList.addView(label("%.2f GB RAM i lirë".format(available/gb),22f,Color.rgb(64,224,210),true))
  candidatesList.addView(label("%.2f GB RAM gjithsej".format(total/gb),13f))
  val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
   max=1000
   this.progress=(percent*10).toInt().coerceIn(0,1000)
  }
  candidatesList.addView(progress,LinearLayout.LayoutParams(-1,dp(12)).apply{topMargin=dp(12);bottomMargin=dp(12)})
  candidatesList.addView(label("Android menaxhon vetë proceset në sfond. Një aplikacion i zakonshëm nuk mund të mbyllë në mënyrë të sigurt të gjitha proceset e tjera.",13f,Color.rgb(170,202,226)))
  action(candidatesList,"🔄","RIFRESKO RAM-IN","Lexo përsëri statistikat"){showProcessManager()}
  action(candidatesList,"📦","MENAXHO APLIKACIONET","Zgjidh aplikacionet për çinstalim"){showInstalledApps()}
  action(candidatesList,"⚙️","HAP CILËSIMET E APLIKACIONEVE","Kontrollo aplikacionet që konsumojnë burime"){
   startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS))
  }
 }
 private fun showInstalledApps(){
  val usageManager=getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
  val end=System.currentTimeMillis()
  val usage=usageManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,end-30L*86400000,end)
  val lastUsed=usage.orEmpty().groupBy{it.packageName}.mapValues{entry->entry.value.maxOfOrNull{it.lastTimeUsed}?:0L}
  val hasUsageAccess=!usage.isNullOrEmpty()
  candidatesList.removeAllViews()
  candidatesList.addView(label("Aplikacionet – zgjidh dhe çinstalo",16f,Color.CYAN,true))
  candidatesList.addView(label("Android kërkon miratim për çdo aplikacion. Nuk hiqen aplikacionet e sistemit.",12f))
  val apps=packageManager.getInstalledApplications(0)
   .filter { it.packageName!=packageName && (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0 }
   .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
  if(!hasUsageAccess) action(candidatesList,"🔐","Lejo statistikat e përdorimit","Për rekomandime më të sakta"){startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))}
  val selected=linkedSetOf<String>()
  val checks=mutableListOf<CheckBox>()
  for(app in apps.take(150)){
   val appName=packageManager.getApplicationLabel(app).toString()
   val last=lastUsed[app.packageName]?:0L
   val detail=if(!hasUsageAccess)"Nuk ka statistika përdorimi" else if(last==0L)"Pa përdorim të regjistruar në 30 ditë" else "Përdorur "+((end-last)/86400000)+" ditë më parë"
   val check=CheckBox(this).apply {
    text=appName+"\\n"+detail
    textSize=14f
    setTextColor(Color.WHITE)
    setOnCheckedChangeListener { _,checked->if(checked)selected.add(app.packageName) else selected.remove(app.packageName) }
   }
   checks.add(check);candidatesList.addView(check)
  }
  action(candidatesList,"☑","Përzgjidh të papërdorurat","Vetëm sipas statistikave të disponueshme"){
   if(hasUsageAccess) apps.take(150).forEachIndexed { index,app ->
    checks[index].isChecked=(lastUsed[app.packageName]?:0L)==0L
   } else Toast.makeText(this,"Lejo statistikat fillimisht",Toast.LENGTH_LONG).show()
  }
  action(candidatesList,"↩","Hiq përzgjedhjen","Mos çinstalo asgjë"){checks.forEach { it.isChecked=false }}
  action(candidatesList,"🗑️","ÇINSTALO TË ZGJEDHURAT","Android do të kërkojë miratim për secilin"){
   if(selected.isEmpty()) Toast.makeText(this,"Zgjidh aplikacionet",Toast.LENGTH_LONG).show()
   else android.app.AlertDialog.Builder(this).setTitle("Çinstalo ${selected.size} aplikacione?")
    .setMessage("Aplikacionet mund të përmbajnë të dhëna personale. Çdo çinstalim kërkon miratim veçmas.")
    .setNegativeButton("Anulo",null).setPositiveButton("Vazhdo"){_,_->
     uninstallQueue.clear();uninstallQueue.addAll(selected);uninstallNext()
    }.show()
  }
  if(apps.isEmpty()) candidatesList.addView(label("Nuk u gjetën aplikacione të dukshme.",13f))
 }
 private fun uninstallNext(){
  if(uninstallQueue.isEmpty()){pendingUninstall=null;showInstalledApps();return}
  val pkg=uninstallQueue.removeAt(0)
  pendingUninstall=pkg
  try{
   startActivityForResult(Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)),uninstallRequestCode)
  }catch(e:Exception){
   result.text="Çinstalimi nuk u nis: ${e.message}"
   uninstallQueue.clear();pendingUninstall=null
  }
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
  if(scanRunning)return
  scanRunning=true
  liveStatus.text=tr("DUKE SKANUAR...")
  liveMetric.text="Po numërohen skedarët"
  ringUpdater?.invoke(0.02f)
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
    android.provider.MediaStore.MediaColumns.SIZE,
    android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
   val groupedBySize=mutableMapOf<Long,MutableList<Triple<Uri,String,Long>>>()
   for(base in collections){
    try{
     contentResolver.query(base,projection,null,null,null)?.use{cursor->
      val idColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
      val nameColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
      val sizeColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
      val dateColumn=cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
      while(cursor.moveToNext() && total<20000){
       val id=cursor.getLong(idColumn)
       val name=cursor.getString(nameColumn)?:"media"
       val size=if(cursor.isNull(sizeColumn))0L else cursor.getLong(sizeColumn)
       val file=android.content.ContentUris.withAppendedId(base,id)
       val date=if(cursor.isNull(dateColumn))0L else cursor.getLong(dateColumn)
       mediaDates[file]=date
       total++;bytes+=size
       if(size>0L)groupedBySize.getOrPut(size){mutableListOf()}.add(Triple(file,name,date))
      }
     }
    }catch(_:Exception){errors++}
   }
   val possible=groupedBySize.values.filter{it.size>1}.flatten()
   val workCount=possible.size
   runOnUiThread{
    liveStatus.text=tr("DUKE KRAHASUAR...")
    liveMetric.text="$total skedarë u numëruan • $workCount kandidatë"
    ringUpdater?.invoke(if(workCount==0)1f else 0.05f)
   }
   var processed=0
   for((file,name,_) in possible){
    try{
     val md=MessageDigest.getInstance("SHA-256")
     contentResolver.openInputStream(file)?.use{input->
      val buf=ByteArray(65536)
      while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}
      val digest=md.digest().joinToString(""){"%02x".format(it)}
      hashes.getOrPut(digest){mutableListOf()}.add(file to name)
     } ?: run{errors++}
    }catch(_:Exception){errors++}
    processed++
    if(processed%10==0||processed==workCount){
     val done=processed
     runOnUiThread{
      liveMetric.text="$done / $workCount kandidatë të krahasuar"
      ringUpdater?.invoke(if(workCount==0)1f else (0.05f+0.95f*done/workCount).coerceAtMost(1f))
     }
    }
   }
   val duplicates=hashes.values.filter { it.size>1 }
   val candidates=duplicates.flatMap { group -> group.sortedWith(compareByDescending<Pair<Uri,String>> { mediaDates[it.first] ?: 0L }.thenBy { it.second }).drop(1) }
   val message="Skanimi i medias përfundoi.\nSkedarë: $total\\nMadhësia: ${bytes/1048576} MB\\nGrupe duplikatesh: ${duplicates.size}\nGabime leximi: $errors\\n\\n"+
    duplicates.take(25).joinToString("\\n\\n"){group->group.joinToString("\\n"){it.second}}+
    "\\n\\nAsgjë nuk është fshirë. Dokumentet dhe dosjet e mbrojtura nga Android kërkojnë zgjedhje të veçantë."
   runOnUiThread {
    cleanupCandidates.clear();cleanupCandidates.addAll(candidates)
    mediaCandidates.clear();mediaCandidates.addAll(candidates)
    verifiedDuplicates.clear();verifiedDuplicates.addAll(candidates.map { it.first })
    scanRunning=false
    ringUpdater?.invoke(1f)
    liveStatus.text=tr("SKANIMI PËRFUNDOI")
    liveMetric.text="$total skedarë • ${candidates.size} kopje të tepërta"
    lastReport=message;result.text=message
    showSelection(candidates,"Duplikate identike: ${candidates.size} kopje të tepërta",true)
    val prefs=getSharedPreferences("scan_history",MODE_PRIVATE)
    val now=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
    val prev=prefs.getString("recent","") ?: ""
    prefs.edit().putString("recent",("$now — Media: $total skedarë, ${duplicates.size} grupe\\n"+prev).lines().take(20).joinToString("\\n")).apply()
   }
  }.start()
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  if(requestCode==compressPickCode){if(resultCode==RESULT_OK)compressPhotos(data);return}
  if(requestCode==uninstallRequestCode){
   val pkg=pendingUninstall
   val stillInstalled=if(pkg==null) false else try{
    packageManager.getPackageInfo(pkg,0);true
   }catch(_:android.content.pm.PackageManager.NameNotFoundException){false}
   result.text=if(stillInstalled)"Aplikacioni $pkg është ende i instaluar." else "Çinstalimi i $pkg u verifikua."
   if(stillInstalled) uninstallQueue.clear()
   uninstallNext()
   return
  }
  if(requestCode==deleteRequestCode){
   if(resultCode==RESULT_OK){
    val count=pendingTrash.size
    result.text="Android miratoi kërkesën për $count skedarë. Kontrollo koshin e Galerisë/My Files; hapësira mund të mos lirohet derisa koshi të zbrazet."
    selectedMedia.clear()
    candidatesList.removeAllViews()
   }else result.text="Pastrimi u anulua. Nuk u ndryshua asgjë."
   pendingTrash.clear()
   return
  }
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
   val candidates=duplicates.flatMap { it.drop(1) }
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

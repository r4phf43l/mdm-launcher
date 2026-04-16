package com.cityhub.agent

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Helper que aplica wallpaper exclusivamente na **tela de bloqueio** (lock screen).
 */
object WallpaperHelper {

    fun setLockWpUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            android.util.Log.w("WallpaperHelper", "Não foi possível persistir permissão para URI: $uri")
        }
        PrefsManager.setLockWpUri(uri.toString())
    }

    fun apply(context: Context, force: Boolean = true, onResult: (success: Boolean, message: String) -> Unit = { _, _ -> }) {
        Thread {
            try {
                val wm = WallpaperManager.getInstance(context)
                if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) {
                    onResult(false, "Bloqueio de sistema para definir wallpaper.")
                    return@Thread
                }

                // Sempre aplica se force=true ou se a feature estiver habilitada
                val lockEnabled = PrefsManager.getLockWpEnabled()
                if (lockEnabled || force) {
                    val lockBitmap = resolveBitmapForLock(context)
                    if (lockBitmap != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // Tenta aplicar tanto na tela de bloqueio quanto na home para garantir consistência
                            // já que o app agora atua como Launcher principal.
                            
                            val rect = android.graphics.Rect(0, 0, lockBitmap.width, lockBitmap.height)
                            
                            // 1. Lock Screen
                            wm.setBitmap(lockBitmap, rect, true, WallpaperManager.FLAG_LOCK)
                            
                            // 2. System/Home (Se for o launcher, isso reforça a marca)
                            wm.setBitmap(lockBitmap, rect, true, WallpaperManager.FLAG_SYSTEM)
                        } else {
                            wm.setBitmap(lockBitmap)
                        }
                        lockBitmap.recycle()
                        PrefsManager.setPendingWpUpdate(false)
                    }
                }
                onResult(true, "Operação concluída.")
            } catch (e: Exception) {
                android.util.Log.e("WallpaperHelper", "ERRO: ${e.localizedMessage}", e)
                onResult(false, "Erro: ${e.localizedMessage}")
            }
        }.start()
    }

    fun clear(context: Context, isLockScreen: Boolean) {
        try {
            val wm = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val flag = if (isLockScreen) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                wm.clear(flag)
            } else {
                wm.clear()
            }
        } catch (_: Exception) {}
    }

    private fun resolveBitmapForLock(context: Context): Bitmap? {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val (targetWidth, targetHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            metrics.bounds.width() to metrics.bounds.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }

        val lockUri = PrefsManager.getLockWpUri()
        if (!lockUri.isNullOrBlank()) {
            val bmp = loadBitmapFromUri(context, lockUri, targetWidth, targetHeight)
            if (bmp != null) {
                val finalBmp = fitCenter(context, bmp, targetWidth, targetHeight, PrefsManager.getBgColor())
                if (bmp != finalBmp) bmp.recycle()
                return finalBmp
            }
        }
        return colorToBitmap(targetWidth, targetHeight, PrefsManager.getBgColor())
    }

    private fun loadBitmapFromUri(context: Context, uriStr: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val finalUri = if (uri.scheme?.startsWith("http") == true) {
                downloadToCache(context, uriStr) ?: return null
            } else {
                uri
            }

            if (finalUri.scheme == "file") {
                val path = finalUri.path ?: return null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, options)
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(finalUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(finalUri)?.use { 
                    BitmapFactory.decodeStream(it, null, options) 
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadToCache(context: Context, urlStr: String): Uri? {
        return try {
            val cacheFile = File(context.cacheDir, "downloaded_wallpaper.img")
            val connection = URL(urlStr).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.getInputStream().use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun fitCenter(context: Context, source: Bitmap, targetWidth: Int, targetHeight: Int, bgColorStr: String): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val color = try { Color.parseColor(bgColorStr) } catch (_: Exception) { Color.parseColor("#1A1A2E") }
        canvas.drawColor(color)

        val sourceWidth = source.width.toFloat()
        val sourceHeight = source.height.toFloat()

        // Calcula a escala para Center Crop (preencher a tela mantendo proporção)
        val scale = Math.max(
            targetWidth.toFloat() / sourceWidth,
            targetHeight.toFloat() / sourceHeight
        )

        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale

        val dx = (targetWidth - scaledWidth) / 2f
        val dy = (targetHeight - scaledHeight) / 2f

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, matrix, paint)

        return result
    }

    private fun colorToBitmap(width: Int, height: Int, colorStr: String): Bitmap? {
        return try {
            val color = Color.parseColor(colorStr)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(color)
            bitmap
        } catch (_: Exception) { null }
    }
}

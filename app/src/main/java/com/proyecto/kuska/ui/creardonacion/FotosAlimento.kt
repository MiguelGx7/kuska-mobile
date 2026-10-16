package com.proyecto.kuska.ui.creardonacion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/**
 * Fotos temporales de los alimentos, en cache/fotos/ (compartida por el FileProvider).
 * Se borran al enviar la donación o al salir del flujo.
 */
object FotosAlimento {

    /** Lado mayor de la foto reducida y calidad JPEG: así queda muy por debajo de los 5 MB (RN-11a). */
    const val LADO_MAXIMO = 1600
    const val CALIDAD_JPEG = 85

    private const val CARPETA = "fotos"

    fun carpeta(context: Context): File = File(context.cacheDir, CARPETA).apply { mkdirs() }

    /** Archivo vacío para que la cámara escriba la foto, y su URI content:// para TakePicture. */
    fun nuevoArchivoCamara(context: Context): Pair<File, Uri> {
        val archivo = File(carpeta(context), "camara_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
        return archivo to uri
    }

    /**
     * Lee la imagen de [origen], la gira según su EXIF, reduce su lado mayor a [LADO_MAXIMO] y la
     * guarda como JPEG en un archivo nuevo de la carpeta. Devuelve el archivo, o null si no se pudo leer.
     */
    suspend fun reducir(context: Context, origen: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(origen)?.use { BitmapFactory.decodeStream(it, null, limites) }
            if (limites.outWidth <= 0 || limites.outHeight <= 0) return@withContext null

            // inSampleSize (potencia de 2) evita cargar la foto completa en memoria.
            val opciones = BitmapFactory.Options().apply {
                inSampleSize = factorMuestreo(limites.outWidth, limites.outHeight, LADO_MAXIMO)
            }
            val decodificada = resolver.openInputStream(origen)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return@withContext null

            val orientacion = resolver.openInputStream(origen)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val final = escalarYGirar(decodificada, orientacion)
            val destino = File(carpeta(context), "foto_${UUID.randomUUID()}.jpg")
            destino.outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, it) }
            if (final !== decodificada) decodificada.recycle()
            final.recycle()
            destino
        } catch (_: IOException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Borra la foto si existe y está en la carpeta temporal (nunca toca otros archivos). */
    fun borrar(context: Context, ruta: String?) {
        val archivo = ruta?.let(::File) ?: return
        if (archivo.parentFile == carpeta(context)) archivo.delete()
    }

    fun borrarTodas(context: Context) {
        carpeta(context).listFiles()?.forEach { it.delete() }
    }

    /** Mayor potencia de 2 que deja el lado mayor en al menos [ladoMaximo]. */
    internal fun factorMuestreo(ancho: Int, alto: Int, ladoMaximo: Int): Int {
        var factor = 1
        while (max(ancho, alto) / (factor * 2) >= ladoMaximo) factor *= 2
        return factor
    }

    private fun escalarYGirar(bitmap: Bitmap, orientacion: Int): Bitmap {
        val matriz = Matrix()
        val ladoMayor = max(bitmap.width, bitmap.height)
        if (ladoMayor > LADO_MAXIMO) {
            val escala = LADO_MAXIMO.toFloat() / ladoMayor
            matriz.postScale(escala, escala)
        }
        when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matriz.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matriz.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matriz.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matriz.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matriz.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matriz.postRotate(90f)
                matriz.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matriz.postRotate(270f)
                matriz.postScale(-1f, 1f)
            }
        }
        if (matriz.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
    }
}

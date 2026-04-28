/*
 * Round Calculator for Wear OS
 * Copyright (C) 2026 [stanissay]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package stanissay.wear.calc

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.watchface.complications.data.ComplicationData

class CalculatorComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithResource(this, R.drawable.calculator_vector),
                type = SmallImageType.PHOTO
            ).build(),
            contentDescription = PlainComplicationText.Builder("Open Calculator").build()
        )
            .setTapAction(pendingIntent)
            .build()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithResource(this, R.drawable.calculator_vector),
                type = SmallImageType.PHOTO
            ).build(),
            contentDescription = PlainComplicationText.Builder("Preview").build()
        ).build()
    }
}
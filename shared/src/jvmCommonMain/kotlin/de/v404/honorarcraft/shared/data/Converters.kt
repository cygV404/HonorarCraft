package de.v404.honorarcraft.shared.data

import androidx.room.TypeConverter
import java.math.BigDecimal

class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        if (value.isNullOrBlank()) return null
        return value.replace(",", ".").toBigDecimalOrNull()
    }
}

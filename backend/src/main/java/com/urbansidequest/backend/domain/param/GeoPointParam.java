package com.urbansidequest.backend.domain.param;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class GeoPointParam {

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitudeGcj02;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitudeGcj02;

    public BigDecimal getLongitudeGcj02() {
        return this.longitudeGcj02;
    }

    public void setLongitudeGcj02(BigDecimal longitudeGcj02) {
        this.longitudeGcj02 = longitudeGcj02;
    }

    public BigDecimal getLatitudeGcj02() {
        return this.latitudeGcj02;
    }

    public void setLatitudeGcj02(BigDecimal latitudeGcj02) {
        this.latitudeGcj02 = latitudeGcj02;
    }
}

package com.urlshortener.dto.v1;

import com.urlshortener.model.DeviceTypeStat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Device-type click distribution for the last 30 days")
public class DeviceDistributionResponse {

    @Schema(description = "Per-device-type click counts")
    private List<DeviceTypeStat> data;
}

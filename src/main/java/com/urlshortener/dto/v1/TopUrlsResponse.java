package com.urlshortener.dto.v1;

import com.urlshortener.model.TopUrlStat;
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
@Schema(description = "Top 10 URLs by click count for today (UTC)")
public class TopUrlsResponse {

    @Schema(description = "Top URLs ranked by click count for today")
    private List<TopUrlStat> data;
}

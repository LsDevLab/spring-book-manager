package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

// Shows the difference between HyperLogLog (approximate, fixed memory) and Set (exact, linear memory).
// Returned by the simulation endpoint so you can see the tradeoff with real numbers.
@Data
@AllArgsConstructor
@Schema(description = "HyperLogLog vs Set comparison results")
public class HllComparisonDTO {

    @Schema(description = "Number of unique userIds added to both structures", example = "50000")
    private int usersSimulated;

    @Schema(description = "PFCOUNT result — approximate count (standard error ~0.81%)", example = "49814")
    private long hllCount;

    @Schema(description = "SCARD result — exact count", example = "50000")
    private long setCount;

    @Schema(description = "MEMORY USAGE of the HyperLogLog key in bytes (~12-14KB always)", example = "14384")
    private long hllMemoryBytes;

    @Schema(description = "MEMORY USAGE of the Set key in bytes (grows linearly with members)", example = "2800000")
    private long setMemoryBytes;

    @Schema(description = "How many times less memory HLL uses compared to Set", example = "194.7")
    private double memorySavingsFactor;

    @Schema(description = "Difference between exact and approximate count", example = "186")
    private long countDifference;

    @Schema(description = "Error percentage of HLL vs exact count", example = "0.37")
    private double errorPercentage;
}

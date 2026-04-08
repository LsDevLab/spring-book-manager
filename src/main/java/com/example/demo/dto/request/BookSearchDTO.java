package com.example.demo.dto.request;


import com.example.demo.model.Topic;
import com.example.demo.repository.specification.BookSpecifications;
import com.example.demo.repository.specification.dtoToSpecBuilderUtil.WithSpecification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search filters for querying books")
public class BookSearchDTO {

    @Schema(description = "Filter by title (partial match)", example = "Clean")
    @WithSpecification(method = "titleContains", specClass = BookSpecifications.class)
    private String title;

    @Schema(description = "Filter by author (partial match)", example = "Martin")
    @WithSpecification(method = "authorContains", specClass = BookSpecifications.class)
    private String author;

    @Schema(description = "Filter by topic", example = "BACKEND")
    @WithSpecification(method = "hasTopic", specClass = BookSpecifications.class)
    private Topic topic;

    @Schema(description = "Minimum number of pages", example = "100")
    @WithSpecification(method = "minPages", specClass = BookSpecifications.class)
    private Integer minPages;

    @Schema(description = "Maximum number of pages", example = "500")
    @WithSpecification(method = "maxPages", specClass = BookSpecifications.class)
    private Integer maxPages;

}

package com.example.demo.dto.request;


import com.example.demo.model.Topic;
import com.example.demo.repository.specification.BookSpecifications;
import com.example.demo.repository.specification.dtoToSpecBuilderUtil.WithSpecification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookSearchDTO {

    @WithSpecification(method = "titleContains", specClass = BookSpecifications.class)
    private String title;

    @WithSpecification(method = "authorContains", specClass = BookSpecifications.class)
    private String author;

    @WithSpecification(method = "hasTopic", specClass = BookSpecifications.class)
    private Topic topic;

    @WithSpecification(method = "minPages", specClass = BookSpecifications.class)
    private Integer minPages;

    @WithSpecification(method = "maxPages", specClass = BookSpecifications.class)
    private Integer maxPages;

}

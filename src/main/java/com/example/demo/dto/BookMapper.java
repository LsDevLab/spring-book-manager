package com.example.demo.dto;

import com.example.demo.dto.request.BookRequestDTO;
import com.example.demo.dto.response.BookResponseDTO;
import com.example.demo.model.Book;
import org.mapstruct.Mapper;

import java.util.List;

// MapStruct — compile-time code generator for DTO ↔ Entity mapping.
// componentModel = "spring" — registers as a Spring bean, injectable via @RequiredArgsConstructor.
// MapStruct matches fields by name automatically. Generated code in target/generated-sources/.
// Alternative to manual toEntity()/fromEntity() methods in the DTOs.
@Mapper(componentModel = "spring")
public interface BookMapper {

    Book toEntity(BookRequestDTO dto);

    BookResponseDTO toResponseDTO(Book book);

    List<BookResponseDTO> toResponseDTOList(List<Book> books);

}
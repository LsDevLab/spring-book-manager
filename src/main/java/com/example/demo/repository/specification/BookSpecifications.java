package com.example.demo.repository.specification;

import com.example.demo.dto.request.BookSearchDTO;
import com.example.demo.model.Book;
import com.example.demo.model.Topic;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.function.Function;

// Utility class providing reusable Specification<Book> lambdas.
// Each method returns a single JPA Predicate (a WHERE clause fragment).
// These are composed with .and() / .or() in the service layer to build
// dynamic queries — no need for a separate repository method per filter combo.
//
// Specification<T> is a @FunctionalInterface with one method:
//   Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb)
//     - root:  the entity (like SQL table alias) — root.get("field") accesses columns
//     - query: the full query — rarely needed for simple predicates
//     - cb:    CriteriaBuilder — factory for predicates (like, equal, >=, <=, and, or)
public class BookSpecifications {

    private BookSpecifications() {}

    /** Case-insensitive partial match on title. SQL: {@code WHERE LOWER(title) LIKE '%value%'}. */
    public static Specification<Book> titleContains(String title) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    /** Case-insensitive partial match on author. SQL: {@code WHERE LOWER(author) LIKE '%value%'}. */
    public static Specification<Book> authorContains(String author){
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("author")), "%" + author.toLowerCase()+ "%");
    }

    // Exact match — SQL: WHERE topic = 'BACKEND'
    public static Specification<Book> hasTopic(Topic topic){
        return (root, query, cb) ->
                cb.equal(root.get("topic"), topic);
    }

    // Range filter — SQL: WHERE total_pages >= min
    public static Specification<Book> minPages(Integer min){
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("totalPages"), min);
    }

    // Range filter — SQL: WHERE total_pages <= max
    public static Specification<Book> maxPages(Integer max){
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("totalPages"), max);
    }

}


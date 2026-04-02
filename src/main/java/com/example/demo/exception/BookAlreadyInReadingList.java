package com.example.demo.exception;

import com.example.demo.model.Book;
import com.example.demo.model.User;

public class BookAlreadyInReadingList extends RuntimeException {
    public BookAlreadyInReadingList(User user, Book book) {
        super("User " + user.getUsername() + " has already the book " + book.getTitle() + " in his reading list");
    }
}

package com.example.demo.exception;

import com.example.demo.model.Book;
import com.example.demo.model.User;

import java.util.UUID;

public class BookAlreadyInReadingList extends RuntimeException {

    public BookAlreadyInReadingList() {
        super("The book is already in reading list");
    }


}

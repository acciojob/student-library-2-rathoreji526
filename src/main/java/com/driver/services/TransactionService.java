package com.driver.services;

import com.driver.models.*;
import com.driver.repositories.BookRepository;
import com.driver.repositories.CardRepository;
import com.driver.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class TransactionService {

    @Autowired
    BookRepository bookRepository;

    @Autowired
    CardRepository cardRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @Value("${books.max_allowed}")
    public int max_allowed_books;

    @Value("${books.max_allowed_days}")
    public int getMax_allowed_days;

    @Value("${books.fine.per_day}")
    public int fine_per_day;

    public String issueBook(int cardId, int bookId) throws Exception {
        //check whether bookId and cardId already exist
        Book book = bookRepository.findById(bookId).orElseThrow(()->new Exception("Book is either unavailable or not present"));
        Card card = cardRepository.findById(cardId).orElseThrow(()->new Exception("Card is invalid"));

        //conditions required for successful transaction of issue book:
        //1. book is present and available

        if(!book.isAvailable())throw new Exception("Book is either unavailable or not present");
        if(card.getCardStatus() != CardStatus.ACTIVATED)throw new Exception("Card is invalid");

        // If it fails: throw new Exception("Book is either unavailable or not present");
        //2. card is present and activated
        // If it fails: throw new Exception("Card is invalid");

        //3. number of books issued against the card is strictly less than max_allowed_books
        if(card.getBooks().size() >=  max_allowed_books){
            throw new Exception("Book limit has reached for this card");
        }
        // If it fails: throw new Exception("Book limit has reached for this card");
        //If the transaction is successful, save the transaction to the list of transactions and return the id

        //avaliable book , card limit not exeeced
        Transaction t = new Transaction();
        t.setBook(book);
        t.setCard(card);
        t.setFineAmount(0);
        t.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        t.setIssueOperation(true);
        book.setAvailable(false);
        book.setCard(card);
        card.getBooks().add(book);
        Transaction t1 = transactionRepository.save(t);

        //Note that the error message should match exactly in all cases

       return ""+t1.getId(); //return transactionId instead
    }



    public Transaction returnBook(int cardId, int bookId) throws Exception{

        List<Transaction> transactions = transactionRepository.find(cardId, bookId, TransactionStatus.SUCCESSFUL, true);
        Transaction transaction = transactions.get(transactions.size() - 1);

        Book book = bookRepository.findById(bookId)
                .orElseThrow(()-> new Exception("Book not found"));
        Card card = cardRepository.findById(cardId)
                .orElseThrow(()-> new Exception("Card not found"));

        Date transactionDate = transaction.getTransactionDate();
        LocalDate localTransactionDate = transactionDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        int days = (int)ChronoUnit.DAYS.between(localTransactionDate, LocalDate.now());
        int fine = 0;
        if(days > getMax_allowed_days){
            fine = (fine_per_day * (days - getMax_allowed_days));
        }

        Transaction returnTransaction = new Transaction();

        returnTransaction.setBook(book);
        returnTransaction.setCard(card);
        returnTransaction.setFineAmount(fine);
        returnTransaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        returnTransaction.setIssueOperation(false);


        book.setAvailable(true);
        book.setCard(null);
        card.getBooks().remove(book);

        //for the given transaction calculate the fine amount considering the book has been returned exactly when this function is called
        //make the book available for other users
        //make a new transaction for return book which contains the fine amount as well

        Transaction returnBookTransaction  = transactionRepository.save(returnTransaction);
        return returnBookTransaction; //return the transaction after updating all details
    }
}

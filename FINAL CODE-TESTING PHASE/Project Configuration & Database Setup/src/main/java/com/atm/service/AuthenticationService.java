package com.atm.service;

import com.atm.dao.AccountDAO;
import com.atm.dao.UserDAO;
import com.atm.exception.AccountNotFoundException;
import com.atm.exception.InvalidPINException;
import com.atm.infrastructure.LVDS;
import com.atm.infrastructure.NetworkManager;
import com.atm.model.Account;
import com.atm.model.Customer;

import java.sql.SQLException;
import java.io.IOException;

public class AuthenticationService {
    private final UserDAO userDAO = new UserDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final LVDS lvds = LVDS.getInstance();

    public Account authenticate(String accountNumber, String pin)
            throws SQLException, AccountNotFoundException, InvalidPINException {

        if (NetworkManager.isOnline()) {
            Account account = accountDAO.getAccountByNumber(accountNumber);
            if (account == null) {
                throw new AccountNotFoundException(
                        "Account Number " + accountNumber + " does not exist.");
            }

            Customer customer = userDAO.getCustomerById(account.getCustomerId());
            if (customer == null || !customer.getPin().equals(pin)) {
                throw new InvalidPINException("Invalid PIN supplied.");
            }

            // Cache the latest central snapshot for future offline use.
            lvds.updateLocalAccount(account);
            lvds.cacheCustomer(customer);
            return account;
        }

        // Offline login uses the ATM's own LVDS snapshot.
        try {
            Account localAccount = lvds.getAccount(accountNumber);
            if (localAccount == null) {
                throw new AccountNotFoundException(
                        "Account " + accountNumber
                                + " is not available in this ATM's LVDS snapshot.");
            }

            if (!lvds.verifyCachedPIN(localAccount.getCustomerId(), pin)) {
                throw new InvalidPINException("Invalid PIN supplied.");
            }

            return localAccount;
        } catch (IOException e) {
            throw new SQLException("Unable to read local LVDS account data.", e);
        }
    }

    /**
     * Verifies the PIN again before every sensitive customer operation.
     * Offline operation uses the ATM's cached SHA-256 PIN digest.
     */
    public void verifyOperationPIN(Account account, String pin)
            throws SQLException, InvalidPINException {
        if (account == null) {
            throw new InvalidPINException("Invalid PIN.");
        }

        if (!NetworkManager.isOnline()) {
            if (!lvds.verifyCachedPIN(account.getCustomerId(), pin)) {
                throw new InvalidPINException("Invalid PIN.");
            }
            return;
        }

        Customer customer = userDAO.getCustomerById(account.getCustomerId());
        if (customer == null || !customer.getPin().equals(pin)) {
            throw new InvalidPINException("Invalid PIN.");
        }
    }
}

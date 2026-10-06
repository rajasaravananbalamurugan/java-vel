package com.atm.service;

import com.atm.dao.AccountDAO;
import com.atm.infrastructure.LVDS;
import com.atm.infrastructure.NetworkManager;
import com.atm.model.Account;

import java.io.IOException;
import java.sql.SQLException;

public class AccountService {
    private final AccountDAO accountDAO = new AccountDAO();
    private final LVDS lvds = LVDS.getInstance();

    public Account getAccount(String accountNumber) throws SQLException {
        if (NetworkManager.isOnline()) {
            return accountDAO.getAccountByNumber(accountNumber);
        }
        try {
            return lvds.getAccount(accountNumber);
        } catch (IOException e) {
            throw new SQLException("Unable to read account from local LVDS.", e);
        }
    }
}

package br.net.brjdevs.steven.bran.games.tictactoe;

import br.net.brjdevs.steven.bran.games.engine.event.*;
import br.net.brjdevs.steven.bran.games.tictactoe.events.InvalidMoveEvent;
import br.net.brjdevs.steven.bran.games.tictactoe.events.MoveEvent;

public class TicTacToeListener extends GameEventListener {
    
    @Override
    public void onGiveUp(GiveUpEvent event) {
    }
    
    @Override
    public void onLeaveGame(LeaveEvent event) {
    }
    
    @Override
    public void onWin(WinEvent event) {
        TicTacToe ticTacToe = ((TicTacToe) event.getGame());
        event.getGame().getLocation().send("**" + ticTacToe.getInfo().getPlayers().get(ticTacToe.getPlayer() == 0 ? 1 : 0)
                .getUser(event.getShard().getJDA()).getName() + "** won!").queue();
    }
    
    @Override
    public void onLoose(LooseEvent event) {
        event.getGame().getLocation().send("It's a cat's game!").queue();
    }
    
    public void onInvalidMove(InvalidMoveEvent event) {
        TicTacToe ticTacToe = ((TicTacToe) event.getGame());
        event.getGame().getLocation().send(ticTacToe.getTurn()
                .getUser(event.getShard().getJDA()).getName() + ", " +
                (event.getTile().getPlayer() == ticTacToe.getPlayer() ? "you are" : "your opponent is") + " already using that tile!").queue();
    }
    
    public void onMove(MoveEvent event) {
        event.getGame().getLocation().send(event.getGame().toString()).queue();
    }
}
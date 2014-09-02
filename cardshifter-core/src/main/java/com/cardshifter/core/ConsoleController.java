package com.cardshifter.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.cardshifter.core.actions.TargetAction;
import com.cardshifter.core.actions.UsableAction;

public class ConsoleController {
	private final Game game;

	public ConsoleController(final Game game) {
		this.game = Objects.requireNonNull(game, "game");;
	}

	public void play() {
		Scanner input = new Scanner(System.in);
		while (!game.isGameOver()) {
			outputGameState();
			List<UsableAction> actions = game.getAllActions().stream().filter(action -> action.isAllowed()).collect(Collectors.toList());
			outputList(actions);
			
			String in = input.nextLine();
			if (in.equals("exit")) {
				break;
			}
			
			handleActionInput(actions, in, input);
		}
		print("--------------------------------------------");
		outputGameState();
		print("Game over!");
	}

	private void handleActionInput(final List<UsableAction> actions, final String in, Scanner input) {
		Objects.requireNonNull(actions, "actions");
		Objects.requireNonNull(in, "in");
		print("Choose an action:");
		
		try {
			int value = Integer.parseInt(in);
			if (value < 0 || value >= actions.size()) {
				print("Action index out of range: " + value);
				return;
			}
			
			UsableAction action = actions.get(value);
			print("Action " + action);
			if (action.isAllowed()) {
				if (action instanceof TargetAction) {
					TargetAction targetAction = (TargetAction) action;
					List<Targetable> targets = targetAction.findTargets();
					if (targets.isEmpty()) {
						print("No available targets for action");
						return;
					}
					
					outputList(targets);
					print("Enter target index:");
					int targetIndex = Integer.parseInt(input.nextLine());
					if (value < 0 || value >= actions.size()) {
						print("Target index out of range: " + value);
						return;
					}
					
					Targetable target = targets.get(targetIndex);
					targetAction.perform(target);
				}
				else {
					action.perform();
				}
				print("Action performed");
			}
			else {
				print("Action is not allowed");
			}
		}
		catch (NumberFormatException ex) {
			print("Illegal action index: " + in);
		}
	}

	private void outputList(final List<?> actions) {
		Objects.requireNonNull(actions, "actions");
		print("------------------");
		ListIterator<?> it = actions.listIterator();
		while (it.hasNext()) {
			print(it.nextIndex() + ": " + it.next());
		}
	}

	private void outputGameState() {
		print("------------------");
		print(this.game);
		for (Player player : game.getPlayers()) {
			print(player);
			player.getActions().values().forEach(action -> print(4, "Action: " + action));
			printLua(4, player.data); // TODO: Some LuaData should probably be hidden from other players, or even from self.
		}
		
		for (Zone zone : game.getZones()) {
			print(zone);
			if (zone.isKnownToPlayer(game.getCurrentPlayer())) {
				zone.getCards().forEach(card -> {
					print(4, card);
					card.getActions().values().forEach(action -> print(8, "Action: " + action));
					printLua(8, card.data);
				});
			}
		}
	}

	private void print(final Object object) {
		print(0, object);
	}
	
	private void print(final int indentation, final Object object) {
		System.out.println(indent(indentation) + object.toString());
	}
	
	private void printLua(final int indentation, final LuaValue value) {
		processLuaTable(value.checktable(), (k, v) -> print(indentation, k + ": " + v));
	}
	
	private void processLuaTable(final LuaTable luaTable, final BiConsumer<LuaValue, LuaValue> pairConsumer) {
		luaTable.checktable();
		Objects.requireNonNull(pairConsumer, "pairConsumer");
		//search uses last key to find next key, starts with NIL
		LuaValue key = LuaValue.NIL;
		while (true) {
			Varargs pair = luaTable.next(key);
			key = pair.arg1();
			if (key.isnil()) {
				//no more keys
				break;
			}
			pairConsumer.accept(key, pair.arg(2));
		}
	}
	
	private String indent(final int amount) {
		if (amount == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder(amount);
		for (int i = 0; i < amount; i++) {
			sb.append(' ');
		}
		return sb.toString();
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		CommandLineOptions options = new CommandLineOptions();
		JCommander jcommander = new JCommander(options);
		try {
			jcommander.parse(args);
		}
		catch (ParameterException ex) {
			System.out.println(ex.getMessage());
			jcommander.usage();
			return;
		}
		InputStream file = options.getScript() == null ? Game.class.getResourceAsStream("start.lua") : new FileInputStream(new File(options.getScript()));
		
		Game game = new Game(file, options.getRandom());
		game.getEvents().startGame(game);
		new ConsoleController(game).play();		
	}
}

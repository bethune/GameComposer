package de.mirkosertic.gamecomposer.contentarea.gamescene;

import de.mirkosertic.gamecomposer.GameObjectClipboardContent;
import de.mirkosertic.gamecomposer.ObjectSelectedEvent;
import de.mirkosertic.gamecomposer.ObjectUpdatedEvent;
import de.mirkosertic.gamecomposer.ShutdownEvent;
import de.mirkosertic.gamecomposer.contentarea.ContentChildController;
import de.mirkosertic.gameengine.camera.CameraComponent;
import de.mirkosertic.gameengine.core.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;

import javax.enterprise.event.Event;
import java.util.List;

public class GameSceneEditorController implements ContentChildController<GameScene> {

    @FXML
    BorderPane centerBorderPane;

    @FXML
    CheckBox snapToGrid;

    @FXML
    CheckBox renderPhysics;

    @FXML
    TextField gridsizeWidth;

    @FXML
    TextField gridsizeHeight;

    private GameScene gameScene;
    private Node view;
    private EditorJXGameView gameView;
    private Thread gameLoopThread;
    private CameraComponent cameraComponent;
    private Event<ObjectSelectedEvent> objectSelectedEventEvent;

    private GameObjectInstance dndCreateInstance;
    private GameObjectInstance draggingInstance;
    private Position draggingMouseWorldPosition;

    GameSceneEditorController initialize(GameScene aScene, Node aView, EditorJXGameView aGameView, Thread aGameLoopThread, CameraComponent aCameraComponent, Event<ObjectSelectedEvent> aSelectedEvent) {

        centerBorderPane.setCenter(aGameView);

        gameScene = aScene;
        view = aView;
        gameView = aGameView;
        gameLoopThread = aGameLoopThread;
        cameraComponent = aCameraComponent;
        objectSelectedEventEvent = aSelectedEvent;

        gameView.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent aEvent) {
                onDragOver(aEvent);
            }
        });
        gameView.setOnDragEntered(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent aEvent) {
                onDragEntered(aEvent);
            }
        });
        gameView.setOnDragExited(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent aEvent) {
                onDragExited(aEvent);
            }
        });
        gameView.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent aEvent) {
                setOnDragDropped(aEvent);
            }
        });
        gameView.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent aEvent) {
                onMouseDragged(aEvent);
            }
        });
        gameView.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent aEvent) {
                onMousePressed(aEvent);
            }
        });
        gameView.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent aEvent) {
                onMouseReleased(aEvent);
            }
        });
        gameView.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent aEvent) {
                onMouseClicked(aEvent);
            }
        });

        gridsizeWidth.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String aOldValue, String aNewValue) {
                gameView.gridsizeWidthProperty().set(Integer.valueOf(aNewValue));
            }
        });
        gridsizeHeight.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String aOldValue, String aNewValue) {
                gameView.gridsizeHeightProperty().set(Integer.valueOf(aNewValue));
            }
        });
        gridsizeWidth.setText(Integer.toString(gameView.gridsizeWidthProperty().get()));
        gridsizeHeight.setText(Integer.toString(gameView.gridsizeHeightProperty().get()));

        gameView.renderPhysicsDebugProperty().bind(renderPhysics.selectedProperty());
        gameView.snapToGridProperty().bind(snapToGrid.selectedProperty());
        return this;
    }

    private void onDragOver(DragEvent aEvent) {
        if (aEvent.getGestureSource() != this && aEvent.getDragboard().hasContent(GameObjectClipboardContent.FORMAT)) {
            aEvent.acceptTransferModes(TransferMode.LINK);
        }

        if (dndCreateInstance != null) {
            Position theNewPosition = cameraComponent.transformFromScreen(new Position(aEvent.getX(), aEvent.getY()));
            gameScene.updateObjectInstancePosition(dndCreateInstance, theNewPosition);
        }

        aEvent.consume();
    }

    private void onDragEntered(DragEvent aEvent) {
        Dragboard theDragBoard = aEvent.getDragboard();
        if (theDragBoard.hasContent(GameObjectClipboardContent.FORMAT)) {
            GameObjectClipboardContent theContent = (GameObjectClipboardContent) theDragBoard.getContent(GameObjectClipboardContent.FORMAT);
            GameObject theGameObject = gameScene.findGameObjectByID(theContent.getGameObjectId());

            GameObjectInstanceFactory theInstanceFactory = new GameObjectInstanceFactory(gameScene.getRuntime());
            GameObjectInstance theInstance = theInstanceFactory.createFrom(theGameObject);
            theInstance.setPosition(cameraComponent.transformFromScreen(new Position(aEvent.getX(), aEvent.getY())));
            theInstance.setSize(new Size(76, 76));

            gameScene.addGameObjectInstance(theInstance);

            dndCreateInstance = theInstance;

            aEvent.consume();
        }
    }

    private void onDragExited(DragEvent aEvent) {
        if (dndCreateInstance != null) {
            gameScene.removeGameObjectInstance(dndCreateInstance);
            dndCreateInstance = null;
            aEvent.consume();
        }
    }

    private void setOnDragDropped(DragEvent aEvent) {
        if (dndCreateInstance != null) {
            aEvent.setDropCompleted(true);
            aEvent.consume();
            if (snapToGrid.isSelected()) {
                gameScene.updateObjectInstancePosition(dndCreateInstance, gameView.snapToGrid(dndCreateInstance.getPosition()));
            }
            dndCreateInstance = null;
        }
    }

    private void onMousePressed(MouseEvent aEvent) {
        Position theScreenPosition = new Position(aEvent.getX(), aEvent.getY());
        Position theWorldPosition = cameraComponent.transformFromScreen(theScreenPosition);
        List<GameObjectInstance> theFoundInstances = gameScene.findAllAt(theWorldPosition);
        if (theFoundInstances.size() == 1) {
            draggingInstance = theFoundInstances.get(0);
            draggingMouseWorldPosition = theWorldPosition;

            objectSelectedEventEvent.fire(new ObjectSelectedEvent(draggingInstance));
        }
    }

    private void onMouseDragged(MouseEvent aEvent) {
        if (draggingInstance != null) {
            Position theScreenPosition = new Position(aEvent.getX(), aEvent.getY());
            Position theWorldPosition = cameraComponent.transformFromScreen(theScreenPosition);
            float theDX = theWorldPosition.x - draggingMouseWorldPosition.x;
            float theDY = theWorldPosition.y - draggingMouseWorldPosition.y;

            Position theObjectPosition = draggingInstance.getPosition();
            gameScene.updateObjectInstancePosition(draggingInstance, new Position(theObjectPosition.x + theDX, theObjectPosition.y + theDY));

            draggingMouseWorldPosition = theWorldPosition;
        }
    }

    private void onMouseReleased(MouseEvent aEvent) {
        if (draggingInstance != null && snapToGrid.isSelected()) {
            gameScene.updateObjectInstancePosition(draggingInstance, gameView.snapToGrid(draggingInstance.getPosition()));
        }
        draggingMouseWorldPosition = null;
        draggingInstance = null;
    }

    @Override
    public GameScene getEditingObject() {
        return gameScene;
    }

    @Override
    public void processKeyPressedEvent(KeyEvent aKeyEvent) {
        gameScene.getRuntime().getEventManager().fire(new KeyPressedGameEvent(GameKeyCode.valueOf(aKeyEvent.getCode().name())));
    }

    @Override
    public void processKeyReleasedEvent(KeyEvent aKeyEvent) {
        gameScene.getRuntime().getEventManager().fire(new KeyReleasedGameEvent(GameKeyCode.valueOf(aKeyEvent.getCode().name())));
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void addedAsTab() {
        gameView.startTimer();
        gameLoopThread.start();
    }

    @Override
    public void removed() {
        gameScene.getRuntime().getEventManager().fire(new GameShutdownEvent());
        gameView.stopTimer();
        gameLoopThread.interrupt();
    }

    @Override
    public void onObjectSelected(ObjectSelectedEvent aEvent) {
        gameView.onObjectSelected(aEvent);
    }

    @Override
    public void onShutdown(ShutdownEvent aEvent) {
        gameView.stopTimer();
        gameLoopThread.interrupt();
        gameScene.getRuntime().getEventManager().fire(new GameShutdownEvent());
    }

    public void onMouseClicked(MouseEvent aEvent) {
        Position theClickPosition = new Position(aEvent.getX(), aEvent.getY());
        for (GameObjectInstance theInstance : cameraComponent.getObjectsToDrawInRightOrder(gameScene)) {
            if (theInstance.contains(theClickPosition)) {
                objectSelectedEventEvent.fire(new ObjectSelectedEvent(theInstance));
            }
        }
    }

    public void onObjectUpdated(Tab aTab, ObjectUpdatedEvent aEvent) {
        if (aEvent.getObject() == gameScene) {
            aTab.setText(gameScene.getName());
        }
    }
}
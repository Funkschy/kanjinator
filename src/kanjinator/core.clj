(ns kanjinator.core
  (:gen-class)
  (:require
   [kanjinator.language :refer [perform-ocr split-words]]
   [kanjinator.languages.jp :refer [japanese]]
   [kanjinator.preprocess :refer [default-process-pipeline preprocess-image]])
  (:import
   (java.awt Color Frame Graphics2D Point Rectangle Robot)
   (java.awt.event MouseEvent MouseListener MouseMotionListener WindowEvent)
   (javax.swing JFrame JPanel SwingUtilities WindowConstants)
   (nu.pattern OpenCV)))

(def language (japanese))

(defn rect->xywh [rect]
  (let [{:keys [^Point start ^Point end]} rect]
    (when (and start end)
      [(min (.getX start) (.getX end))
       (min (.getY start) (.getY end))
       (abs (- (.getX end) (.getX start)))
       (abs (- (.getY end) (.getY start)))])))

(defn- make-screenshot [window rect]
  (let [translate #(when %
                     (doto (.clone ^Point %)
                       (SwingUtilities/convertPointToScreen window)))
        rect (-> rect
                 (update :start translate)
                 (update :end translate))
        robot (Robot.)
        [x y w h] (rect->xywh rect)]
    (when (and x y w h)
      (-> robot
          (.createScreenCapture (Rectangle. x y w h))
          (preprocess-image default-process-pipeline)))))

(defn- render-selection-rect [^Graphics2D g state]
  (when-let [[x y w h] (-> state :rect rect->xywh)]
    (doto g
      (.setColor (Color. 30 130 200 100))
      (.fillRect x y w h))))

(defn- process-selection [^JFrame window state]
  (when-let [screenshot-rect (:screenshot-rect state)]
    (let [screenshot (make-screenshot window screenshot-rect)]
      (->> screenshot
           (perform-ocr language)
           (split-words language)
           (prn))
      (.dispatchEvent window (WindowEvent. window WindowEvent/WINDOW_CLOSING)))))

(defn draw-panel [state ^JFrame window]
  (let [panel (proxy [JPanel] []
                (paintComponent [^Graphics2D g]
                  ;; (proxy-super paintComponent g)
                  (let [state @state]
                    (render-selection-rect g state)
                    (process-selection window state))))]
    (.setBackground panel (Color. 0 0 0 0))
    panel))

(defn mouse-motion-listener [rect ^JFrame frame]
  (proxy [MouseMotionListener] []
    (mouseDragged [^MouseEvent e]
      (swap! rect
             (fn [rect]
               (-> rect
                   (update-in [:rect :start] (fnil identity (.getPoint e)))
                   (assoc-in [:rect :end] (.getPoint e)))))
      ;; update the frame to draw the current rect
      (.update frame (.getGraphics frame)))
    (mouseMoved [^MouseEvent e])))

(defn mouse-listener [state]
  (proxy [MouseListener] []
    (mouseReleased [^MouseEvent e]
      (swap! state (fn [{:keys [rect]}] {:screenshot-rect rect}))
      ;; force redraw so we can make a screenshot in the paintComponent method of the JPanel
      ;; we can't do it here, because then the selection rectangle would be in the screenshot
      ;; aswell, which can screw with the OCR
      (let [window (.getComponent e)]
        (.update window (.getGraphics window))))
    (mousePressed [e])
    (mouseClicked [e])
    (mouseEntered [e])
    (mouseExited [e])))

(defn -main []
  (OpenCV/loadLocally)
  (let [frame (new JFrame)
        state (atom {})
        panel (draw-panel state frame)]
    (doto frame
      (.setDefaultCloseOperation WindowConstants/EXIT_ON_CLOSE)
      (.setLocationRelativeTo nil)
      (.setUndecorated true)
      (.setBackground (Color. 0 0 0 0))
      (.setExtendedState Frame/MAXIMIZED_BOTH))

    (doto frame
      (.addMouseMotionListener (mouse-motion-listener state frame))
      (.addMouseListener (mouse-listener state))
      (.setContentPane panel)
      (.pack)
      (.setVisible true))))

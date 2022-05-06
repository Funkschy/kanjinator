(ns kanjinator.ui
  (:require
   [kanjinator.config :refer [config windows?]]
   [kanjinator.language :refer [get-ocr-words lookup]]
   [clojure.string :as str])
  (:import
   [java.awt Color Frame Graphics2D Point Rectangle Robot Toolkit Font]
   [java.awt.event MouseEvent MouseListener MouseMotionListener]
   [javax.swing JFrame JPanel SwingUtilities WindowConstants JLabel BoxLayout]
   [javax.swing.border EmptyBorder]))

(def bg-color
  (if windows?
    (Color. 0 0 0 1) ;; windows can't handle pure tranparency
    (Color. 0 0 0 0)))

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
    ;; TODO: check min size
    (when (and x y w h)
      (.createScreenCapture robot (Rectangle. x y w h)))))

(defn- result-panel [entries]
  (let [panel (JPanel.)
        font  (.getFont panel)]
    (.setBorder panel (EmptyBorder. 10 10 10 10))
    (.setLayout panel (BoxLayout. panel BoxLayout/Y_AXIS))
    (doseq [e entries]
      (let [term    (:dict/search-term e)
            e-panel (JPanel.)
            label   (JLabel. term)]
        (.setFont label (Font. (.getName font) (.getStyle font) 24))
        (.setLayout e-panel (BoxLayout. e-panel BoxLayout/Y_AXIS))
        (.add e-panel label)
        (doseq [w (:dict/words e)]
          (let [jp    (str/join " " (vals (select-keys w [:dict/writing :dict/reading])))
                en    (str/join ", " (get w :dict/meanings))
                label (JLabel. (str jp ": " en))]
            (.setFont label (Font. (.getName font) (.getStyle font) 14))
            (.add e-panel label)))
        (.add panel e-panel)))
    panel))

(defn- display-results [^JFrame window rect entries]
  (prn rect entries)
  (doto window
    (.setExtendedState Frame/NORMAL)
    (.setLocation (:end rect))
    (.setContentPane (result-panel entries))
    (.setBackground (Color. 255 255 255 255))
    (.pack)
    (.setVisible true)))

(defn- render-selection-rect [^Graphics2D g state]
  (when-let [[x y w h] (-> state :rect rect->xywh)]
    (doto g
      (.setColor (Color. 30 130 200 100))
      (.fillRect x y w h))))

(defn- process-selection [^JFrame window state]
  (when-let [screenshot-rect (:screenshot-rect state)]
    (let [screenshot (make-screenshot window screenshot-rect)
          language   ((resolve (get config :current-language)))]
      (.setVisible window false)
      (.setContentPane window (JPanel.))
      (->> screenshot
           (get-ocr-words language)
           (mapcat (partial lookup language))
           (doall)
           (display-results window screenshot-rect))
      ;; (.dispatchEvent window (WindowEvent. window WindowEvent/WINDOW_CLOSING))
      )))

(defn draw-panel [state ^JFrame window]
  (let [panel (proxy [JPanel] []
                (paintComponent [^Graphics2D g]
                  (proxy-super paintComponent g)
                  (let [state @state]
                    (render-selection-rect g state)
                    (process-selection window state))))]
    (doto panel
      (.setMinimumSize (.getScreenSize (Toolkit/getDefaultToolkit)))
      (.setOpaque false)
      (.setBackground bg-color))))

(defn mouse-motion-listener [rect ^JFrame frame]
  (proxy [MouseMotionListener] []
    (mouseDragged [^MouseEvent e]
      (swap! rect
             (fn [rect]
               (-> rect
                   (update-in [:rect :start] (fnil identity (.getPoint e)))
                   (assoc-in [:rect :end] (.getPoint e)))))
      ;; update the frame to draw the current rect
      ;; I have no idea why, but for some reason one way of updating only works on linux and the
      ;; other only works on windows. This took me quite a few hours to find.
      ;; Write once run anywhere btw
      (if windows?
        (-> frame .getContentPane .repaint)
        (.update frame (.getGraphics frame))))
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

(defn run-application-window []
  (SwingUtilities/invokeLater
   (fn []
     (let [frame (new JFrame)
           state (atom {})
           panel (draw-panel state frame)]
       (doto frame
         (.setDefaultCloseOperation WindowConstants/EXIT_ON_CLOSE)
         (.setLocationRelativeTo nil)
         (.setUndecorated true)
         (.setBackground bg-color)
         (.addMouseMotionListener (mouse-motion-listener state frame))
         (.addMouseListener (mouse-listener state))
         (.setContentPane panel)
         (.pack)
         (.setVisible true)
         (.setExtendedState Frame/MAXIMIZED_BOTH))))))

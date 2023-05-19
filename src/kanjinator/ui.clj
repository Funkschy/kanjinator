(ns kanjinator.ui
  (:require
   [clojure.java.io :refer [input-stream resource]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [kanjinator.config :refer [config]]
   [kanjinator.language :refer [get-ocr-text lookup split-words]])
  (:import
   [java.awt
    Color
    Component
    Dimension
    Font
    FontMetrics
    Frame
    Graphics2D
    GraphicsConfiguration
    GraphicsDevice
    MouseInfo
    Point
    Robot]
   [java.awt.event
    WindowEvent
    WindowFocusListener
    MouseEvent
    MouseListener
    MouseMotionListener]
   [java.awt.image BufferedImage]
   [javax.swing
    Box
    BoxLayout
    JFrame
    JLabel
    JPanel
    JSeparator
    JTextField
    SwingUtilities
    UIManager
    WindowConstants]
   [javax.swing.border EmptyBorder]))

(defn rect->xywh [rect]
  (let [{:keys [^Point start ^Point end]} rect]
    (when (and start end)
      [(min (.getX start) (.getX end))
       (min (.getY start) (.getY end))
       (abs (- (.getX end) (.getX start)))
       (abs (- (.getY end) (.getY start)))])))

(defn- trim-screenshot [^BufferedImage screenshot rect]
  (let [[x y w h] (rect->xywh rect)]
    (when (and x y w h (> w 10) (> h 10))
      (.getSubimage screenshot x y w h))))

(defn- get-render-width [font-metrics text]
  (if-not (empty? text)
    (.stringWidth ^FontMetrics font-metrics text)
    0))

(defn- get-entry-max-text-widths [font-metrics dict-entry]
  (->> (:dict/words dict-entry)
       (map (juxt :dict/writing :dict/reading))
       (apply map (partial max-key count))
       (map (partial get-render-width font-metrics))))

(defn- get-entries-max-text-widths [font-metrics dict-entries]
  (apply map max (map (partial get-entry-max-text-widths font-metrics) dict-entries)))

(defn- result-panel [full-text entries]
  (let [content-panel (JPanel.)
        font          (Font/createFont Font/TRUETYPE_FONT (input-stream (resource "NotoSansJP-Regular.ttf")))
        header-font   (.deriveFont font 24.0)
        entry-font    (.deriveFont font 14.0)
        entry-metrics (.getFontMetrics content-panel entry-font)
        entry-widths  (get-entries-max-text-widths entry-metrics entries)
        padding       10
        border        padding
        [write-w read-w] (map (partial + padding) entry-widths)]

    (.setBorder content-panel (EmptyBorder. border border border border))
    (.setLayout content-panel (BoxLayout. content-panel BoxLayout/Y_AXIS))

    (doto content-panel
      (.add
       (doto (JTextField. ^String full-text)
         (.setFont header-font)
         (.setEditable false)
         (.setBorder nil)
         (.setForeground (UIManager/getColor "Label.foreground"))))
      ;; invisible separator
      (.add (Box/createVerticalStrut 10))
      (.add (JSeparator.)))

    (doseq [entry entries]
      (let [entry-panel (JPanel.)
            term-label  (JLabel. ^String (:dict/search-term entry))]

        (.setFont term-label header-font)
        (.setLayout entry-panel (BoxLayout. entry-panel BoxLayout/Y_AXIS))
        (.add entry-panel term-label)

        (doseq [{:keys [dict/writing dict/reading dict/meanings]} (:dict/words entry)]
          (let [meaning-str  (str/join ", " meanings)
                word-panel   (JPanel.)
                word-layout  (BoxLayout. word-panel BoxLayout/X_AXIS)
                write-label  (JLabel. ^String writing)
                read-label   (JLabel. ^String reading)
                mean-label   (JLabel. ^String meaning-str)
                label-height (.getHeight entry-metrics)
                write-dim    (Dimension. write-w label-height)
                read-dim     (Dimension. read-w label-height)]

            (.setAlignmentX word-panel Component/LEFT_ALIGNMENT)

            (doto write-label
              (.setFont entry-font)
              (.setPreferredSize write-dim)
              (.setMinimumSize   write-dim)
              (.setMaximumSize   write-dim))

            (doto read-label
              (.setFont entry-font)
              (.setPreferredSize read-dim)
              (.setMinimumSize   read-dim)
              (.setMaximumSize   read-dim))

            (.setFont mean-label entry-font)

            (doto word-panel
              (.setLayout word-layout)
              (.add write-label)
              (.add read-label)
              (.add mean-label))

            (.add entry-panel word-panel)))

        (.add content-panel entry-panel)))

    content-panel))

(defn window-focus-listener []
  (proxy [WindowFocusListener] []
    (windowGainedFocus [^WindowEvent e])
    (windowLostFocus [^WindowEvent e]
      (log/info "Lost focus. Closing")
      ;; exit the application when the user clicks on something else
      (System/exit 0))))

(defn- get-end-location [& {:keys [start end x-off y-off] :or {x-off 0 y-off 0}}]
  (Point. (+ x-off (max (.getX ^Point start) (.getX ^Point end)))
          (+ y-off (max (.getY ^Point start) (.getY ^Point end)))))

(defn- display-results [^JFrame window ^GraphicsConfiguration display-config rect full-text entries]
  (.dispose window)
  ;; just reusing the old window works on linux, but for some reason not on windows
  ;; so just dispose the old one and make a new one
  (if-not (empty? entries)
    (let [bounds (.getBounds display-config)
          x-off  (.getX bounds)
          y-off  (.getY bounds)
          point  (get-end-location :x-off x-off :y-off y-off rect)]
      (doto (new JFrame display-config)
        (.setLocation point)
        (.setUndecorated true)
        (.setContentPane (result-panel full-text entries))
        (.addWindowFocusListener (window-focus-listener))
        (.pack)
        (.setVisible true)))
    (System/exit 0)))

(defn- render-selection-rect [^Graphics2D g state]
  (when-let [[x y w h] (-> state :rect rect->xywh)]
    (doto g
      (.setColor (Color. 30 130 200 100))
      (.fillRect x y w h))))

(defn- process-selection
  [^JFrame window
   {:keys [screenshot screenshot-rect device] :as state}
   dependency-future]
  (when screenshot-rect
    (when-let [screenshot (trim-screenshot screenshot screenshot-rect)]
      (let [language ((resolve (get config :current-language)))]
        (.setFullScreenWindow ^GraphicsDevice device nil)
        (.setVisible window false)
        (.setContentPane window (JPanel.))
        ;; the future contains the call to OpenCV/loadLocally, which needs to finish before we can
        ;; use any opencv functions. By derefing it, we ensure that OpenCV is initialized before we
        ;; use it
        @dependency-future
        (let [text (get-ocr-text language screenshot)]
          (->> text
               (split-words language)
               (mapcat (partial lookup language))
               (doall)
               (display-results window (:display-config state) screenshot-rect text)))
        ;; (.dispatchEvent window (WindowEvent. window WindowEvent/WINDOW_CLOSING))
        ))))

(defn draw-panel [state ^JFrame window ^BufferedImage screenshot dependency-future]
  (proxy [JPanel] []
    (paintComponent [^Graphics2D g]
      (proxy-super paintComponent g)
      (let [state @state]
        (. g drawImage screenshot 0 0 ^JPanel this)
        (render-selection-rect g state)
        (process-selection window state dependency-future)))))

(defn mouse-motion-listener [rect ^JFrame frame]
  (proxy [MouseMotionListener] []
    (mouseDragged [^MouseEvent e]
      (swap! rect
             (fn [rect]
               (-> rect
                   (update-in [:rect :start] (fnil identity (.getPoint e)))
                   (assoc-in [:rect :end] (.getPoint e)))))
      ;; update the frame to draw the current rect
      (-> frame .getContentPane .repaint))
    (mouseMoved [^MouseEvent e])))

(defn mouse-listener [state]
  (proxy [MouseListener] []
    (mouseReleased [^MouseEvent e]
      (swap! state (fn [{:keys [rect] :as state}] (assoc state :screenshot-rect rect)))
      ;; force redraw so we can make a screenshot in the paintComponent method of the JPanel
      ;; we can't do it here, because then the selection rectangle would be in the screenshot
      ;; aswell, which can screw with the OCR
      (let [window (.getComponent e)]
        (.update window (.getGraphics window))))
    (mousePressed [e])
    (mouseClicked [e])
    (mouseEntered [e])
    (mouseExited [e])))

(defn run-application-window [dependency-future]
  (SwingUtilities/invokeLater
   (fn []
     (let [frame  (new JFrame)
           device (.getDevice (MouseInfo/getPointerInfo))
           config (.getDefaultConfiguration device)
           bounds (.getBounds config)

           robot  (new Robot device)
           snap   (.createScreenCapture robot bounds)
           state  (atom {:display-config config :screenshot snap :device device})
           panel  (draw-panel state frame snap dependency-future)]

       (doto frame
         (.setDefaultCloseOperation WindowConstants/EXIT_ON_CLOSE)
         (.setLocationRelativeTo nil)
         (.setUndecorated true)
         (.setResizable true)
         (.setAlwaysOnTop true)
         (.addMouseMotionListener (mouse-motion-listener state frame))
         (.addMouseListener (mouse-listener state))
         (.setContentPane panel)
         (.pack)
         (.setExtendedState Frame/MAXIMIZED_BOTH)
         (.setVisible true))

       (.setFullScreenWindow device frame)))))

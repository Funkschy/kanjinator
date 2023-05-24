(ns kanjinator.clipboard
  (:import
   [java.awt Toolkit]
   [java.awt.datatransfer Clipboard DataFlavor]))

(defn- read-clipboard [^Clipboard c]
  (when (.isDataFlavorAvailable c DataFlavor/stringFlavor)
    (.getData c DataFlavor/stringFlavor)))

(defn read-primary-clipboard []
  (read-clipboard (.getSystemClipboard (Toolkit/getDefaultToolkit))))

(defn read-secondary-clipboard []
  (read-clipboard (.getSystemSelection (Toolkit/getDefaultToolkit))))

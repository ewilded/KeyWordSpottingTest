set xlabel 'False alarms per hour'
set ylabel 'Detection probability'
set key right nobox
set output 'precision_recall_graph.png'
set term png
plot 'precision_recall_graph.4-7.dat' using 2:3 title '4-7 phonemes' with linespoints,'precision_recall_graph.8-11.dat' using 2:3 title '8-11 phonemes' with linespoints,'precision_recall_graph.12-15.dat' using 2:3 title '12-15 phonemes' with linespoints,'precision_recall_graph.16-20.dat' using 2:3 title '16-20 phonemes' with linespoints
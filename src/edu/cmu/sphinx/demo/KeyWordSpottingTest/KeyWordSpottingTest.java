package edu.cmu.sphinx.demo.KeyWordSpottingTest;
import java.io.*;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import edu.cmu.sphinx.frontend.util.AudioFileDataSource;
import edu.cmu.sphinx.linguist.language.grammar.NoSkipGrammar;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.decoder.search.Token;
// This is a modified version of the demo KeywordSpotting App turned into KWS testing code
// It takes one argument;  path to file with list of testing data (audio files paths without extensions)
// coded by ewilded
public class KeyWordSpottingTest
{
			private static String audioTestSetListPath;
			private static List<String> audioFiles = new ArrayList<String>();
			private static Map<String,ArrayList<String>> precisionRecallGraph = new HashMap<String, ArrayList<String>>(); // phonemes count, accuracy, false alarms
			private static Map<String, ArrayList<String>> wordsToSpot = new HashMap<String, ArrayList<String>>();
			private static String[] phonemesRange={"4-7","8-11","12-15","16-20"};
			private static String[] outOfGrammarProbability={"9E-1","5E-1","1E-1","1E-3", "1E-5", "1E-7" ,"1E-9", "1E-11", "1E-15", "1E-20","1E-30", "1E-50", "1E-100","1E-130","1E-150","1E-170","1E-200"};  // it would be nice to keep them in a separate file too, instead of hard-coding, I'll move it when it starts working 
			private static void initDictionaries()
			{
					for(int i=0;i<phonemesRange.length;i++)
					{ 
							String searchDictionaryPath="test_data/words_to_spot."+phonemesRange[i]+".txt";
							try
							{
								FileInputStream fstream = new FileInputStream(searchDictionaryPath);
								DataInputStream in = new DataInputStream(fstream);
								BufferedReader br = new BufferedReader(new InputStreamReader(in));
								String anotherKWord;
								List<String> wordMap=new ArrayList<String>();
								while ((anotherKWord = br.readLine()) != null) wordMap.add(anotherKWord);
								wordsToSpot.put(phonemesRange[i],(ArrayList)wordMap);
								in.close();
							}
							catch (Exception e)
							{       
								System.err.println("Exception Error: " + e.getMessage());
							}                
					}
			}
			private static void gatherAudioPaths()
			{
					String anotherAudioFile;
					File waveFile;
					try
					{
						FileInputStream fstream2 = new FileInputStream(audioTestSetListPath);
						DataInputStream in2 = new DataInputStream(fstream2);
						BufferedReader br2 = new BufferedReader(new InputStreamReader(in2));
						while ((anotherAudioFile = br2.readLine()) != null)
						{
								// iterate over phonemes range again and run separate tests
								waveFile=new File(anotherAudioFile+".wav");
								if(!waveFile.isFile())
								{
									System.out.println("Warning: "+anotherAudioFile+".wav does not exist, skipping the test.");
									continue;
								}    
								audioFiles.add(anotherAudioFile);           
						}
						in2.close();
					}
					catch (Exception e)
					{       
							System.err.println("Exception Error: " + e.getMessage());
					}           				 
					System.out.println("Found "+Integer.toString(audioFiles.size())+" audio files in the test set.");
			}
			private static void performTests()
			{
					for(int j=0;j<outOfGrammarProbability.length;j++)
					{   
							System.out.println("Starting tests with outOfGrammarProbability: "+outOfGrammarProbability[j]+"("+Integer.toString(j+1)+" of "+Integer.toString(outOfGrammarProbability.length)+")");
							/*
							This has been commented out since it doesn't work anyway:
							cm.setGlobalProperty("outOfGrammarProbability",outOfGrammarProbability[j]); // dynamically set current outOfGrammarProbability, other settings are left as they were set directly in the configuration file
							System.out.println("Current outOfGrammarProbability: "+cm.getGlobalProperty("outOfGrammarProbability"));
							Solution:
							13:53 < nshm1> you can add public method in linguist for that
							I don't know how, where, don't have time for this, so I just made a bunch of hard-coded outOfGrammarProbability configs
							*/
							String cfPath="./src/config_"+outOfGrammarProbability[j]+".xml";
							ConfigurationManager cm = new ConfigurationManager(cfPath); // original config
							Recognizer recognizer = (Recognizer) cm.lookup("recognizer");
							List<String> ar = new ArrayList<String>();
							for(int k=0;k<phonemesRange.length;k++)
							{
								NoSkipGrammar grammar = (NoSkipGrammar) cm.lookup("NoSkipGrammar"); // reinitialize grammar, since each phonemesRange has its own list of words to spot (to make these tests reliable, number of words to spot should be the same for all phonemes lengths)
								for(int o=0;o<wordsToSpot.get(phonemesRange[k]).size();o++) grammar.addKeyword(wordsToSpot.get(phonemesRange[k]).get(o));
								int testsCount=0;
        						int correctHits=0;
								int falseAlarmsCount=0;
								int durationSecs=0; // summary duration in miliseconds
								double accuracy=0d;
								for(int n=0;n<audioFiles.size();n++)
								{									   	
											String currPath=audioFiles.get(n)+"."+phonemesRange[k]+".result.txt";
											System.out.println("Working on "+currPath);
											File resultFile=new File(currPath);
											if(!resultFile.isFile()) continue; // not all audio files must have results for all phonemes ranges, they must have at least one
											testsCount++;
											int localFalseAlarmsCount=0;
											int expectedWordsCount=0;
											int localCorrectHits=0;
											int noFillerResultsCount=0;
											double localAccuracy=0d;
											int fileSize=0;
											List<String> currExpectedResult=new ArrayList();                   
											try
											{                 						
												FileInputStream fstream3 = new FileInputStream(currPath);
												DataInputStream in3 = new DataInputStream(fstream3);
												BufferedReader br3 = new BufferedReader(new InputStreamReader(in3));		
												String anotherResult;
												while ((anotherResult = br3.readLine()) != null) currExpectedResult.add(anotherResult.toLowerCase());
												in3.close();
											}
											catch (Exception e)
											{       
													System.err.println("Exception Error: " + e.getMessage());
											}     
											expectedWordsCount=currExpectedResult.size();
											if(expectedWordsCount==0)
											{
													System.out.println("WARNING: "+currPath+" seems empty, skipping this one.");
													continue;
											}
											AudioFileDataSource dataSource = (AudioFileDataSource) cm.lookup("audioFileDataSource");
											System.out.println("KWS for "+audioFiles.get(n)+".wav, "+phonemesRange[k]+" phonemes.");
											try
											{ 
													dataSource.setAudioFile(new URL("file:"+audioFiles.get(n)+".wav"), null);
													fileSize=(int)new File(audioFiles.get(n)+".wav").length();
											}
											catch (Exception e)
											{       
													System.err.println("Exception Error: " + e.getMessage());
											}   	            					 
											durationSecs+=(int)(fileSize/(32*1024)); // 32kilobytes = 1 second
											recognizer.allocate(); 
											Result result = recognizer.recognize();
											String resString=result.getTimedBestResult(false, true);
											
											System.out.println("[DEBUG] "+resString);
											//result.getResultTokens(); does not actually return result tokens, therefore I have to split it, just like I did with my perl version of this program
											List<String> resultTokens= new ArrayList<String>();
											String[] r=resString.split(" ");
											for(int p=0;p<r.length;p++) resultTokens.add(r[p]);
											Pattern wordSpotted = Pattern.compile("(\\w+)\\(\\d+\\.\\d+,\\d+\\.\\d+\\)"); //	  family(43.72,44.21)
											System.out.println("Expected words count: "+Integer.toString(currExpectedResult.size()));
											for (int l=0;l<resultTokens.size();l++)
											{
													String currWord=resultTokens.get(l);
													Matcher ma = wordSpotted.matcher(currWord);
        											if (!ma.matches()) continue; 
        											currWord=ma.group(1);
													// System.out.println("verifying result: "+currWord);
													noFillerResultsCount++;
													for(int m=0;m<currExpectedResult.size();m++)
													{
															if(currWord.equals(currExpectedResult.get(m)))
															{
																	System.out.println("good hit: "+currWord);
																	localCorrectHits++;
																	correctHits++;
																	currExpectedResult.remove(m); // remove the match from the list, so we know how many are left (no timing compared, so there can occur some minor inaccuracies, like negative + false positive of the same word in other section would be treated as a good hit, be aware of this fact)
																	break;
															}
													}
											}
											recognizer.deallocate();
											localAccuracy=localCorrectHits/expectedWordsCount;
											accuracy+=localAccuracy;
											localFalseAlarmsCount=noFillerResultsCount-localCorrectHits; // the number of results that did not match to expected list
											if(localFalseAlarmsCount<0) localFalseAlarmsCount=0;
											falseAlarmsCount+=localFalseAlarmsCount;
											System.out.println("Matched "+Integer.toString(localCorrectHits)+" of "+Integer.toString(expectedWordsCount)+", false alarms met: "+Integer.toString(localFalseAlarmsCount)+".");
									} // end of foreach over all wav files
									System.out.println("[DEBUG] overall duration in seconds: "+Integer.toString(durationSecs));
									if(testsCount>0) accuracy/=testsCount;
									double durationHours=(double)durationSecs/(double)3600.0;
									ar.add(phonemesRange[k]);
									ar.add(Double.toString(accuracy));
									ar.add(Double.toString(falseAlarmsCount/durationHours));
								} // end of foreach over phonemes ranges
								precisionRecallGraph.put(outOfGrammarProbability[j],(ArrayList)ar);	
				} // end of foreach over outOfGrammarProbability values
				System.out.println("Test set has finished.");
			}
			private static void saveResults()
			{
				System.out.println("Saving results...");
				String phRange;
				for(int j=0;j<outOfGrammarProbability.length;j++)
				{
					try
					{ 
						for(int i=0;i<precisionRecallGraph.get(outOfGrammarProbability[j]).size();i+=3)
						{
							phRange=precisionRecallGraph.get(outOfGrammarProbability[j]).get(i).toString();
							String fname="precision_recall_graph."+phRange+".dat";
							System.out.println("appending to "+fname); // I know it's not elegant to open and close this file several times instead of iterate phoneme count ranges instead, but I didn't have time to change it, however it does the job
							FileWriter fstream4 = new FileWriter(fname,true);
							BufferedWriter out4= new BufferedWriter(fstream4);
							System.out.println(outOfGrammarProbability[j]+"\t"+precisionRecallGraph.get(outOfGrammarProbability[j]).get(i+1).toString()+"\t"+precisionRecallGraph.get(outOfGrammarProbability[j]).get(i+2).toString());
							out4.write(outOfGrammarProbability[j]+"\t"+precisionRecallGraph.get(outOfGrammarProbability[j]).get(i+1).toString()+"\t"+precisionRecallGraph.get(outOfGrammarProbability[j]).get(i+2).toString()+"\n"); // outOfGrammarProbability,accuracy, falseAlarms
							out4.close();
							System.out.println("OK.");
						}
					}
					catch (Exception e)
					{
						System.err.println("Error: " + e.getMessage());
					}
				}       
			}
			public static void main(String Args[]) throws IOException
			{
					audioTestSetListPath=Args[0];
					initDictionaries();
					gatherAudioPaths();
					performTests();
					saveResults();
			}
}
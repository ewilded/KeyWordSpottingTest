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
			private static String cfPath;
			private static ConfigurationManager cm;
			private static Recognizer recognizer;
 			private static AudioFileDataSource dataSource;
			
			// separate counters for each of phonemes ranges
			
			private static int[] testsCount = new int[phonemesRange.length];
			private static int[] localExpectedWordsCount = new int[phonemesRange.length];
			private static int[] noFillerResultsCount = new int[phonemesRange.length];
			private static double[] accuracy= new double[phonemesRange.length];
			//private static int[] correctHits = new int[phonemesRange.length];
			private static int[] localCorrectHits = new int[phonemesRange.length];		
			private static int[] falseAlarmsCount = new int[phonemesRange.length];			
			private static int[] localFalseAlarmsCount = new int[phonemesRange.length];			
			private static double[] durationSecs = new double[phonemesRange.length]; // summary duration in milliseconds
			
			private static int fileSize;
			private static NoSkipGrammar grammar;
			
			private static void initDictionaries()
			{
					for(int i=0;i<phonemesRange.length;i++)
					{ 
							File oldResult=new File("precision_recall_graph."+phonemesRange[i]+".dat");
							oldResult.delete();
							String searchDictionaryPath="test_data/words_to_spot."+phonemesRange[i]+".txt";
							File toSpotFile=new File(searchDictionaryPath);
							if(toSpotFile.isFile())
							{
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
							System.out.println("\n\nStarting tests with outOfGrammarProbability: "+outOfGrammarProbability[j]+"("+Integer.toString(j+1)+" of "+Integer.toString(outOfGrammarProbability.length)+")");
							/*
							This has been commented out since it doesn't work anyway:
							cm.setGlobalProperty("outOfGrammarProbability",outOfGrammarProbability[j]); // dynamically set current outOfGrammarProbability, other settings are left as they were set directly in the configuration file
							System.out.println("Current outOfGrammarProbability: "+cm.getGlobalProperty("outOfGrammarProbability"));
							Solution:
							13:53 < nshm1> you can add public method in linguist for that
							I don't know how, where, don't have time for this, so I just made a bunch of hard-coded outOfGrammarProbability configs
							*/             
							cfPath="./src/config_"+outOfGrammarProbability[j]+".xml";
							cm = new ConfigurationManager(cfPath); 
							recognizer = (Recognizer) cm.lookup("recognizer");
							grammar = (NoSkipGrammar) cm.lookup("NoSkipGrammar"); // reinitialize grammar, since each phonemesRange has its own list of words to spot (to make these tests reliable, number of words to spot should be the same for all phonemes lengths)
							for(int k=0;k<phonemesRange.length;k++)
							{
											testsCount[k]=0;
	     									//correctHits[k]=0;
											falseAlarmsCount[k]=0;
											accuracy[k]=0d;
											durationSecs[k]=0;
											if(wordsToSpot.containsKey(phonemesRange[k]))
											for(int o=0;o<wordsToSpot.get(phonemesRange[k]).size();o++) 
											{
												grammar.addKeyword(wordsToSpot.get(phonemesRange[k]).get(o));
											}											
							}	
							for(int n=0;n<audioFiles.size();n++)
							{
									System.out.println("\n\nKWS for "+audioFiles.get(n)+".wav");
									dataSource = (AudioFileDataSource) cm.lookup("audioFileDataSource");
									List<String> currExpectedResult=new ArrayList();
									fileSize=0;     
									try
									{ 
										dataSource.setAudioFile(new URL("file:"+audioFiles.get(n)+".wav"), null);
										fileSize=(int)new File(audioFiles.get(n)+".wav").length();
									}
									catch (Exception e)
									{       
											System.err.println("Exception Error: " + e.getMessage());
									}   	   
									
									// this for should be moved to separate method (we'll do this after testing the code)
									for(int k=0;k<phonemesRange.length;k++)
									{								
	     									localCorrectHits[k]=0;
											localFalseAlarmsCount[k]=0;
											noFillerResultsCount[k]=0;			
											String currPath=audioFiles.get(n)+"."+phonemesRange[k]+".result.txt";
											File resultFile=new File(currPath);											
											if(!resultFile.isFile()) continue; // not all audio files must have results for all phonemes ranges, they must have at least one       
											localExpectedWordsCount[k]=0;
											try
											{                 						
												FileInputStream fstream3 = new FileInputStream(currPath);
												DataInputStream in3 = new DataInputStream(fstream3);
												BufferedReader br3 = new BufferedReader(new InputStreamReader(in3));		
												String anotherResult;
												while ((anotherResult = br3.readLine()) != null) 
												{
														currExpectedResult.add(anotherResult.toLowerCase());
														localExpectedWordsCount[k]++;
												}
												in3.close();
											}
											catch (Exception e)
											{       
													System.err.println("Exception Error: " + e.getMessage());
											}
											if(localExpectedWordsCount[k]==0)
											{
														System.out.println("WARNING: "+currPath+" seems empty, skipping this one.");
														continue;
											}
											System.out.println("localExpectedWordsCount for phonemesRange["+phonemesRange[k]+"]: "+Integer.toString(localExpectedWordsCount[k]));
											testsCount[k]++;
											durationSecs[k]+=(int)(fileSize/(32*1024)); // 32kilobytes = 1 second (calculate summary duration separately for each phonemes range)
      							}
									AudioFileDataSource dataSource = (AudioFileDataSource) cm.lookup("audioFileDataSource");


									recognizer.allocate(); 
									Result result = recognizer.recognize();
									String resString=result.getTimedBestResult(false, true);
									System.out.println("[DEBUG] "+resString);
									List<String> resultTokens= new ArrayList<String>();
									String[] r=resString.split(" ");
									for(int p=0;p<r.length;p++) resultTokens.add(r[p]);
									Pattern wordSpotted = Pattern.compile("(\\w+)\\(\\d+\\.\\d+,\\d+\\.\\d+\\)"); //	  family(43.72,44.21)
									System.out.println("Expected overall words count: "+Integer.toString(currExpectedResult.size()));
									for (int l=0;l<resultTokens.size();l++)
									{
													String currWord=resultTokens.get(l);
													Matcher ma = wordSpotted.matcher(currWord);
        											if (!ma.matches()) continue;
        											currWord=ma.group(1);
													// System.out.println("verifying result: "+currWord);
													//System.out.println("good hit: "+currWord);
													int phHitK=0; // find appropriate phonemes range
													for(phHitK=0;phHitK<phonemesRange.length;phHitK++)
													{																			
															if(wordsToSpot.get(phonemesRange[phHitK]).contains(currWord)) 
															{
																	noFillerResultsCount[phHitK]++; // known phonemes range word hit, now let's find out if it's correct 
																	// hit
																	for(int m=0;m<currExpectedResult.size();m++)
																	{
																		if(currWord.equals(currExpectedResult.get(m)))
																		{																
																				localCorrectHits[phHitK]++;
																				//correctHits[phHitK]++;
																				currExpectedResult.remove(m); // remove the match from the list, so we know how many are left (no timing compared, so there can occur some minor inaccuracies, like negative + false positive of the same word in other section would be treated as a good hit, be aware of this fact)																				
																				break;
																		}
																	}	
																	break;
															}
													}
									}
									recognizer.deallocate();									
									
									for(int k=0;k<phonemesRange.length;k++)
									{
											System.out.println("Collecting local result for "+phonemesRange[k]);
											if(localExpectedWordsCount[k]==0)
											{
												 System.out.println("No expected results for this file for "+phonemesRange[k]+", skipping.");
												 continue; // no tests for this range on current audio file, skip results calculation
											}
											System.out.println("Overall spotted results count: "+Integer.toString(noFillerResultsCount[k])+", localCorrectHits: "+Integer.toString(localCorrectHits[k]));
											double localAcc=(double)localCorrectHits[k]/localExpectedWordsCount[k];
											System.out.println("local accuracy ("+phonemesRange[k]+") "+Double.toString(localAcc));
											accuracy[k]+=localAcc;
											localFalseAlarmsCount[k]=noFillerResultsCount[k]-localCorrectHits[k]; // the number of results that did not match to expected list
											if(localFalseAlarmsCount[k]<0) localFalseAlarmsCount[k]=0;
											falseAlarmsCount[k]+=localFalseAlarmsCount[k];
											System.out.println("False alarms count ("+phonemesRange[k]+"): "+Double.toString(localFalseAlarmsCount[k]));		
									}			
						} // end of foreach over all wav files
						List<String> ar = new ArrayList<String>();
						for(int k=0;k<phonemesRange.length;k++)
						{								
								ar.add(phonemesRange[k]);
								if(testsCount[k]>0)
								{
										System.out.println("Tests count for "+phonemesRange[k]+" for OOGP:"+outOfGrammarProbability[j]+" = "+Integer.toString(testsCount[k]));
										accuracy[k]/=(double)testsCount[k];
										ar.add(Double.toString(accuracy[k]));
										double durationHours=(double)durationSecs[k]/(double)3600.0;
										ar.add(Double.toString(falseAlarmsCount[k]/durationHours));			
								}
								else
								{
										ar.add("NaN");
										ar.add("NaN");
								}
						}
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
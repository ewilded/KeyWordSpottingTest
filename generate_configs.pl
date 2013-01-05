#!/usr/bin/perl
use strict;
my %sphinx_config;
my $config_template_path='/home/parser/KWS/long-audio-aligner/KeyWordSpottingTest/src/config.xml';
# configs are dynamically generated with this line modified <property name="outOfGrammarProbability" value="$current_oogp"/>
print "Generating config files...\n";
my @config_files=();
for(my $i=1;$i<200;$i++)
{
		open(F,"<$config_template_path");
		my $new_config_name="/home/parser/KWS/long-audio-aligner/KeyWordSpottingTest/src/config_1E-$i.xml";
		print "$new_config_name ... ";
		push(@config_files,$new_config_name);
		open(F2,">$new_config_name");
		while(my $row=<F>)
		{
			$row=~s/<property name="outOfGrammarProbability" value=".*?"\/>/<property name="outOfGrammarProbability" value="1E-$i"\/>/;
			print F2 $row;
		}
		close(F1);
		close(F2);
		print "OK\n";
}
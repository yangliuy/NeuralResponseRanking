'''
Generate the MSDialog V2.0 data
MSDialog V2.0  after removing the duplicated last utterance in the dialog context.
In MSDialog V1.0, the last utterance of each dialog context has been duplicated
for once. Fixed this problem in MSDialog V2.0 data.
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

import re
from tqdm import tqdm

def context_utterances_splited(context):
    '''Split a context into multiple utterances seperated by tab
    :param context: context
    :return: a list of utterances splited from the context
    '''
    # <<<[A-Z]{4,5}>>>
    return re.split('<<<[A-Z]{4,5}>>>:', context)

def generate_ms_dialog_v2_data(input_file, output_file):
    print 'generate file : ', output_file
    with open(input_file) as fin, open(output_file, 'w') as fout:
        for l in tqdm(fin):
            tokens = l.split('\t')  # label \t context \t response
            utts = context_utterances_splited(tokens[1])
            # remove the duplicated last utterance in the dialog context.
            #print 'before utts len: ', len(utts)
            utts = utts[0:len(utts)-1]
            #print 'after utts len: ', len(utts)
            fout.write(tokens[0] + '\t'.join(utts) + '\t' + tokens[2])

if __name__ == '__main__':
    for data_part in list(['train', 'valid', 'test']):
        input_basedir = '../../data/ms/ModelInput/'
        output_basedir = '../../data/ms_v2/ModelInput/'
        input_file = input_basedir + data_part + '.txt'
        output_file = output_basedir + data_part +'.txt'
        generate_ms_dialog_v2_data(input_file, output_file)




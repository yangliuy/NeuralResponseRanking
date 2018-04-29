'''
Train word embeddings with word2vec tool by mikolov with training data of ms/udc
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''

import os
import sys

if __name__ == '__main__':
    word2vec_path = '../../data/udc/ModelInput/word2vec_mikolov/word2vec/bin/'

    if len(sys.argv) < 4:
        print 'please input params: data_name, is_read_vocab (0 or 1), model_input_folder (e.g. current_dir or dmn_prf_model_input_body or dmn_model_input )'
        exit(1)
    data_name = sys.argv[1]  # ms or udc or ms_v2
    is_read_vocab = sys.argv[2] # 0 for no read; 1 for read; default is 0
    model_input_folder = sys.argv[3] # model_input_folder (e.g. dmn_prf_model_input_body)
    #wd = sys.argv[4] # size of word embeddings.
    basedir = '../../data/' + data_name + '/ModelInput/'
    # cur_data_dir = basedir + 'dmn_model_input_' + search_field_label + '/'
    cur_data_dir = basedir if model_input_folder == 'current_dir' else basedir + model_input_folder + '/'
    corpus_file = cur_data_dir + 'corpus.txt'
    corpus_text_file = cur_data_dir + 'corpus_text.txt'
    vocab_file = cur_data_dir + 'word_dict.txt'

    print 'generate corpus_file: ', corpus_file
    # generate corpus_text.txt for training the word vectors by word2vec
    with open(corpus_file) as f_in, open(corpus_text_file, 'w') as f_out:
        for l in f_in:
            #print 'l: ', l
            f_out.write(' '.join(l.split()[1:]))

    for wd in list([200]):
        word_embed_file = 'train_word2vec_mikolov_' + str(wd) + 'd.txt' if is_read_vocab == str(1) else 'train_word2vec_mikolov_' \
                                                                                                   + str(wd) + 'd_no_readvocab.txt'
        cmd = word2vec_path + 'word2vec -train ' + corpus_text_file + \
              ' -output ' + cur_data_dir + word_embed_file + ' -debug 2 ' \
              '-size ' + str(wd) + ' -window 5 -negative 10 -hs 0 -binary 0 ' \
                                    '-cbow 0 -threads 5 -min-count 5'
        if is_read_vocab == str(1):
            cmd += ' -read-vocab ' + vocab_file
        print 'run cmd: ', cmd
        os.system(cmd)

